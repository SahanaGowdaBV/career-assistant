package career.assistant.document.service;

import career.assistant.api.ResourceNotFoundException;
import career.assistant.document.customization.CustomizedResumeDocument;
import career.assistant.document.customization.ResumeCustomizer;
import career.assistant.document.customization.TruthfulnessValidator;
import career.assistant.document.dto.ResumeDetailsResponse;
import career.assistant.document.dto.ResumeSummaryResponse;
import career.assistant.document.entity.ResumeVersion;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ParsedResumeDocument;
import career.assistant.document.model.ResumeDownload;
import career.assistant.document.parsing.ResumeParser;
import career.assistant.document.quality.DocumentQualityGate;
import career.assistant.document.repository.ResumeVersionRepository;
import career.assistant.document.storage.ResumeStorage;
import career.assistant.document.storage.StoredResumeObject;
import career.assistant.document.validation.ResumeFileValidator;
import career.assistant.document.validation.ValidatedResumeFile;
import career.assistant.job.entity.Job;
import career.assistant.job.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ResumeService {

    private final ResumeVersionRepository repository;
    private final JobRepository jobRepository;
    private final ResumeStorage storage;
    private final ResumeFileValidator validator;
    private final ResumeParser parser;
    private final ResumeJsonCodec json;
    private final ResumeCustomizer customizer;
    private final TruthfulnessValidator truthfulnessValidator;
    private final DocumentQualityGate qualityGate;

    public ResumeService(
            ResumeVersionRepository repository,
            JobRepository jobRepository,
            ResumeStorage storage,
            ResumeFileValidator validator,
            ResumeParser parser,
            ResumeJsonCodec json,
            ResumeCustomizer customizer,
            TruthfulnessValidator truthfulnessValidator,
            DocumentQualityGate qualityGate
    ) {
        this.repository = repository;
        this.jobRepository = jobRepository;
        this.storage = storage;
        this.validator = validator;
        this.parser = parser;
        this.json = json;
        this.customizer = customizer;
        this.truthfulnessValidator = truthfulnessValidator;
        this.qualityGate = qualityGate;
    }

    @Transactional
    public ResumeDetailsResponse upload(MultipartFile multipartFile, boolean activateAsMaster) {
        ValidatedResumeFile file = validator.validate(multipartFile);
        ParsedResumeDocument parsed = parser.parse(file.content(), file.contentType());
        int version = nextVersionNumber();
        String objectPath = objectPath(file.extension());

        ResumeVersion resume = new ResumeVersion();
        resume.setVersionName("Resume v" + version);
        resume.setVersionNumber(version);
        resume.setFileName(file.sanitizedFilename());
        resume.setOriginalFilename(file.sanitizedFilename());
        resume.setStoragePath(objectPath);
        resume.setContentType(file.contentType());
        resume.setFileSize((long) file.content().length);
        resume.setChecksum(file.checksum());
        resume.setOriginalResume(true);
        resume.setCustomized(false);
        resume.setMasterResume(activateAsMaster);
        applyParsed(resume, parsed);

        storage.store(objectPath, file.content(), file.contentType());
        try {
            if (activateAsMaster) repository.deactivateAllMasters();
            return details(repository.save(resume));
        } catch (RuntimeException exception) {
            safelyDelete(objectPath);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<ResumeSummaryResponse> list() {
        return repository.findAllByOrderByVersionNumberDesc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ResumeDetailsResponse get(UUID id) {
        return details(findRequired(id));
    }

    @Transactional(readOnly = true)
    public ResumeDownload download(UUID id) {
        ResumeVersion resume = findRequired(id);
        if (resume.getStoragePath() == null || resume.getStoragePath().isBlank()) {
            throw new ResourceNotFoundException("Resume " + id + " has no stored file");
        }
        StoredResumeObject object = storage.load(resume.getStoragePath());
        return new ResumeDownload(object.content(), downloadContentType(resume), displayFilename(resume));
    }

    @Transactional
    public ResumeDetailsResponse activateMaster(UUID id) {
        ResumeVersion selected = findRequired(id);
        repository.deactivateAllMasters();
        selected.setMasterResume(true);
        return details(repository.save(selected));
    }

    @Transactional
    public ResumeDetailsResponse reparse(UUID id) {
        ResumeVersion resume = findRequired(id);
        if (resume.getStoragePath() == null || resume.getContentType() == null) {
            throw new ResumeConflictException("Resume does not have a parseable stored file");
        }
        StoredResumeObject object = storage.load(resume.getStoragePath());
        applyParsed(resume, parser.parse(object.content(), resume.getContentType()));
        return details(repository.save(resume));
    }

    @Transactional
    public ResumeDetailsResponse createCustomized(UUID jobId) {
        return createCustomized(jobId, true);
    }

    @Transactional
    public ResumeDetailsResponse createCustomizedVersion(UUID jobId) {
        return createCustomized(jobId, false);
    }

    private ResumeDetailsResponse createCustomized(UUID jobId, boolean reuseExisting) {
        Optional<ResumeVersion> existing = repository.findFirstByJobIdAndCustomizedTrue(jobId);
        if (reuseExisting && existing.isPresent()) return details(existing.get());
        ResumeVersion masterEntity = repository.findFirstByMasterResumeTrue()
                .orElseThrow(() -> new ResumeConflictException("An active master resume is required before customization"));
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job " + jobId + " was not found"));
        ParsedResume master = json.readResume(masterEntity.getStructuredExperience());
        CustomizedResumeDocument customized = customizer.customize(master, jobText(job));
        truthfulnessValidator.validate(master, customized.structured(), masterEntity.getParsedText(), customized.text());
        qualityGate.validateResume(master, customized.structured(), customized.text());

        int version = nextVersionNumber();
        String objectPath = objectPath("docx");
        String filename = validator.sanitizeFilename(job.getTitle() + " resume v" + version, "docx");

        ResumeVersion resume = new ResumeVersion();
        resume.setJobId(jobId);
        resume.setSourceResumeId(masterEntity.getId());
        resume.setVersionName("Customized resume v" + version);
        resume.setVersionNumber(version);
        resume.setFileName(filename);
        resume.setOriginalFilename(filename);
        resume.setStoragePath(objectPath);
        resume.setContentType(ResumeFileValidator.DOCX);
        resume.setFileSize((long) customized.content().length);
        resume.setChecksum(sha256(customized.content()));
        resume.setOriginalResume(false);
        resume.setCustomized(true);
        resume.setMasterResume(false);
        resume.setParsedText(customized.text());
        resume.setStructuredSkills(json.write(customized.structured().skills()));
        resume.setStructuredExperience(json.write(customized.structured()));
        resume.setCustomizationSummary("Reordered verified skills and experience for " + job.getTitle());
        resume.setCustomizationManifest(json.write(Map.of(
                "sourceResumeId", masterEntity.getId(),
                "jobId", jobId,
                "emphasizedSkills", customized.emphasizedSkills(),
                "selectedAchievements", customized.selectedAchievements()
        )));

        storage.store(objectPath, customized.content(), ResumeFileValidator.DOCX);
        try {
            return details(repository.save(resume));
        } catch (RuntimeException exception) {
            safelyDelete(objectPath);
            throw exception;
        }
    }

    @Transactional
    public void delete(UUID id) {
        ResumeVersion resume = findRequired(id);
        if (resume.isMasterResume()) {
            throw new ResumeConflictException("The active master resume cannot be deleted; activate another version first");
        }
        if (resume.getStoragePath() != null) storage.delete(resume.getStoragePath());
        repository.delete(resume);
    }

    @Transactional(readOnly = true)
    public Optional<ResumeVersion> activeMasterEntity() {
        return repository.findFirstByMasterResumeTrue();
    }

    public ParsedResume parsed(ResumeVersion resume) {
        return json.readResume(resume.getStructuredExperience());
    }

    public ResumeVersion findEntity(UUID id) { return findRequired(id); }

    private void applyParsed(ResumeVersion resume, ParsedResumeDocument parsed) {
        resume.setParsedText(parsed.originalText());
        resume.setStructuredSkills(json.write(parsed.structured().skills()));
        resume.setStructuredExperience(json.write(parsed.structured()));
    }

    private synchronized int nextVersionNumber() {
        return repository.findTopByOrderByVersionNumberDesc()
                .map(ResumeVersion::getVersionNumber)
                .map(number -> number + 1)
                .orElse(1);
    }

    private String objectPath(String extension) {
        UUID directory = UUID.randomUUID();
        UUID object = UUID.randomUUID();
        int year = OffsetDateTime.now(ZoneOffset.UTC).getYear();
        return year + "/" + directory + "/" + object + "." + extension;
    }

    private ResumeVersion findRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume " + id + " was not found"));
    }

    private ResumeSummaryResponse summary(ResumeVersion resume) {
        return new ResumeSummaryResponse(
                resume.getId(), displayFilename(resume), resume.getCreatedAt(), safeVersion(resume), status(resume),
                resume.isMasterResume(), resume.isCustomized(), json.readSkills(resume.getStructuredSkills()),
                resume.getContentType(), resume.getFileSize() == null ? 0 : resume.getFileSize()
        );
    }

    private ResumeDetailsResponse details(ResumeVersion resume) {
        return new ResumeDetailsResponse(
                resume.getId(), displayFilename(resume), resume.getCreatedAt(), safeVersion(resume), status(resume),
                resume.isMasterResume(), resume.isCustomized(), resume.getContentType(),
                resume.getFileSize() == null ? 0 : resume.getFileSize(), resume.getChecksum(), resume.getParsedText(),
                json.readResume(resume.getStructuredExperience()), resume.getSourceResumeId(), resume.getJobId(),
                resume.getCustomizationSummary()
        );
    }

    private String displayFilename(ResumeVersion resume) {
        if (resume.getOriginalFilename() != null) return resume.getOriginalFilename();
        if (resume.getFileName() != null) return resume.getFileName();
        return resume.getVersionName();
    }

    private String downloadContentType(ResumeVersion resume) {
        if (resume.getContentType() != null && !resume.getContentType().isBlank()) return resume.getContentType();
        return displayFilename(resume).toLowerCase().endsWith(".pdf")
                ? ResumeFileValidator.PDF
                : ResumeFileValidator.DOCX;
    }

    private int safeVersion(ResumeVersion resume) {
        return resume.getVersionNumber() == null ? 0 : resume.getVersionNumber();
    }

    private String status(ResumeVersion resume) {
        if (resume.isMasterResume()) return "MASTER";
        if (resume.isCustomized()) return "CUSTOMIZED";
        return resume.getParsedText() == null ? "UPLOADED" : "READY";
    }

    private String jobText(Job job) {
        return (job.getTitle() == null ? "" : job.getTitle()) + "\n" +
                (job.getDescription() == null ? "" : job.getDescription());
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void safelyDelete(String objectPath) {
        try {
            storage.delete(objectPath);
        } catch (RuntimeException ignored) {
            // Preserve the original database failure without exposing storage details.
        }
    }
}
