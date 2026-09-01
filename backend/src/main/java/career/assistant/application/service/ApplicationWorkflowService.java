package career.assistant.application.service;

import career.assistant.api.ResourceNotFoundException;
import career.assistant.application.ats.AtsAdapter;
import career.assistant.application.ats.AtsDiscoveryException;
import career.assistant.application.ats.PublicAtsAdapters;
import career.assistant.application.entity.Application;
import career.assistant.application.entity.ApplicationStatus;
import career.assistant.application.entity.ApplicationType;
import career.assistant.application.repository.ApplicationRepository;
import career.assistant.company.repository.CompanyRepository;
import career.assistant.document.dto.ResumeDetailsResponse;
import career.assistant.document.entity.CoverLetter;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ResumeContact;
import career.assistant.document.model.ResumeDownload;
import career.assistant.document.service.CoverLetterService;
import career.assistant.document.service.ResumeConflictException;
import career.assistant.document.service.ResumeService;
import career.assistant.job.entity.Job;
import career.assistant.job.service.JobService;
import career.assistant.jobscore.entity.JobScore;
import career.assistant.jobscore.service.JobScoringService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ApplicationWorkflowService {
    private final ApplicationRepository apps;
    private final JobService jobs;
    private final JobScoringService scoring;
    private final ResumeService resumes;
    private final CoverLetterService letters;
    private final CompanyRepository companies;
    private final PublicAtsAdapters adapters;
    private final ApplicationNotificationService mail;
    private final boolean enabled;
    private final boolean dryRun;
    private final int dailyLimit;
    private final BigDecimal minimumScore;
    private final Set<String> allowlist;

    public ApplicationWorkflowService(
            ApplicationRepository apps,
            JobService jobs,
            JobScoringService scoring,
            ResumeService resumes,
            CoverLetterService letters,
            CompanyRepository companies,
            PublicAtsAdapters adapters,
            ApplicationNotificationService mail,
            @Value("${career.application.auto-apply-enabled:false}") boolean enabled,
            @Value("${career.application.dry-run:true}") boolean dryRun,
            @Value("${career.application.max-real-submissions-daily:1}") int dailyLimit,
            @Value("${career.application.minimum-score:80}") BigDecimal minimumScore,
            @Value("${career.application.allowlisted-domains:}") String domains
    ) {
        this.apps = apps;
        this.jobs = jobs;
        this.scoring = scoring;
        this.resumes = resumes;
        this.letters = letters;
        this.companies = companies;
        this.adapters = adapters;
        this.mail = mail;
        this.enabled = enabled;
        this.dryRun = dryRun;
        this.dailyLimit = dailyLimit;
        this.minimumScore = minimumScore;
        this.allowlist = new HashSet<>(Arrays.stream(domains.split(",")).map(String::trim).map(String::toLowerCase).filter(value -> !value.isBlank()).toList());
    }

    @Transactional
    public WorkflowResponse generate(UUID jobId, boolean lowConfidenceConfirmed) {
        Job job = jobs.findRequired(jobId);
        JobScore score = scoring.findOrScore(job);
        if ("LOW".equals(confidence(score)) && !lowConfidenceConfirmed)
            throw new ResumeConflictException("LOW-confidence jobs require explicit manual confirmation before generation");
        Application existing = apps.findByJobId(jobId).orElse(null);
        if (existing != null && existing.getResumeVersionId() != null && existing.getCoverLetterId() != null)
            return response(existing, job, score);
        ResumeDetailsResponse resume = resumes.createCustomized(jobId);
        CoverLetter letter = letters.generate(job);
        Application application = existing == null ? new Application() : existing;
        application.setJob(job);
        application.setStatus(ApplicationStatus.PENDING_REVIEW);
        application.setApplicationType(ApplicationType.MANUAL);
        application.setApplicationUrl(job.getJobUrl());
        application.setResumeVersionId(resume.id());
        application.setCoverLetterId(letter.getId());
        application.setIdempotencyKey("job:" + jobId);
        adapters.resolve(job.getJobUrl()).ifPresent(adapter -> application.setAdapter(adapter.name()));
        application.setErrorMessage(baseEligibility(job, score, application, adapters.resolve(job.getJobUrl()), List.of()).reason());
        job.setStatus(ApplicationStatus.PENDING_REVIEW.name());
        return response(apps.save(application), job, score);
    }

    @Transactional
    public WorkflowResponse regenerate(UUID applicationId) {
        Application application = required(applicationId);
        if (application.getStatus() == ApplicationStatus.AUTO_APPLIED || application.getStatus() == ApplicationStatus.MANUALLY_APPLIED)
            throw new ResumeConflictException("Submitted applications cannot be regenerated");
        Job job = application.getJob();
        ResumeDetailsResponse resume = resumes.createCustomizedVersion(job.getId());
        CoverLetter letter = letters.generateNewVersion(job);
        application.setResumeVersionId(resume.id());
        application.setCoverLetterId(letter.getId());
        application.setStatus(ApplicationStatus.PENDING_REVIEW);
        application.setReviewedAt(null);
        application.setErrorMessage("New document versions generated; manual document review is required.");
        job.setStatus(ApplicationStatus.PENDING_REVIEW.name());
        return response(apps.save(application), job, scoring.findOrScore(job));
    }

    @Transactional
    public WorkflowResponse review(UUID id, boolean approve, String reason) {
        Application application = required(id);
        if (application.getResumeVersionId() == null || application.getCoverLetterId() == null)
            throw new ResumeConflictException("A complete document package is required");
        application.setStatus(approve ? ApplicationStatus.READY_TO_APPLY : ApplicationStatus.REJECTED);
        application.setReviewedAt(OffsetDateTime.now());
        application.setNotes(reason);
        application.setErrorMessage(null);
        application.getJob().setStatus(application.getStatus().name());
        return response(apps.save(application), application.getJob(), scoring.findOrScore(application.getJob()));
    }

    @Transactional
    public WorkflowResponse returnToReview(UUID id, String reason) {
        Application application = required(id);
        application.setStatus(ApplicationStatus.PENDING_REVIEW);
        application.getJob().setStatus(ApplicationStatus.PENDING_REVIEW.name());
        application.setNotes(reason);
        application.setErrorMessage(null);
        return response(apps.save(application), application.getJob(), scoring.findOrScore(application.getJob()));
    }

    @Transactional
    public WorkflowResponse markManuallyApplied(UUID id, boolean confirmed, String confirmation) {
        if (!confirmed) throw new ResumeConflictException("Explicit confirmation is required before marking an application as manually applied");
        Application application = required(id);
        if (application.getStatus() != ApplicationStatus.READY_TO_APPLY)
            throw new ResumeConflictException("Only a reviewed Ready to Apply application can be marked manually applied");
        if (application.getResumeVersionId() == null || application.getCoverLetterId() == null)
            throw new ResumeConflictException("A complete document package is required");
        OffsetDateTime now = OffsetDateTime.now();
        application.setStatus(ApplicationStatus.MANUALLY_APPLIED);
        application.getJob().setStatus(ApplicationStatus.MANUALLY_APPLIED.name());
        application.setAppliedAt(now);
        application.setSubmittedAt(now);
        application.setConfirmationId(confirmation == null || confirmation.isBlank() ? "MANUAL" : confirmation.trim());
        application.setErrorMessage(null);
        JobScore score = scoring.findOrScore(application.getJob());
        Application saved = apps.save(application);
        mail.sendVerifiedSuccessOnce(saved, String.valueOf(score.getScore()));
        return response(saved, application.getJob(), score);
    }

    @Transactional
    public WorkflowResponse run(UUID id) {
        Application application = required(id);
        Job job = application.getJob();
        JobScore score = scoring.findOrScore(job);
        Optional<AtsAdapter> resolved = adapters.resolve(job.getJobUrl());
        if (resolved.isEmpty()) return pending(application, job, score, PublicAtsAdapters.unsupportedReason(job.getJobUrl()));
        AtsAdapter adapter = resolved.get();
        AtsAdapter.FormDefinition form;
        AtsAdapter.PreparedApplication prepared;
        try {
            form = adapter.discover(job);
            prepared = prepare(form, application);
        } catch (AtsDiscoveryException exception) {
            return pending(application, job, score, exception.getMessage());
        }
        List<String> missing = prepared.missingRequired(form);
        Eligibility gate = baseEligibility(job, score, application, resolved, missing);
        if (!gate.eligible()) return pending(application, job, score, gate.reason());
        application.setAdapter(adapter.name());
        if (dryRun || !enabled) {
            AtsAdapter.AdapterResult result = adapter.dryRun(job, application, prepared);
            if (result.outcome() != AtsAdapter.Outcome.DRY_RUN_READY) return pending(application, job, score, result.reason());
            application.setStatus(ApplicationStatus.READY_TO_APPLY);
            job.setStatus(ApplicationStatus.READY_TO_APPLY.name());
            application.setErrorMessage(result.reason());
            return response(apps.save(application), job, score);
        }
        return pending(application, job, score, "Real submission transport is disabled; manual review is required.");
    }

    @Transactional(readOnly = true)
    public DryRunPreview preview(UUID id) {
        Application application = required(id);
        Job job = application.getJob();
        JobScore score = scoring.findOrScore(job);
        AtsAdapter adapter = adapters.resolve(job.getJobUrl())
                .orElseThrow(() -> new ResumeConflictException(PublicAtsAdapters.unsupportedReason(job.getJobUrl())));
        AtsAdapter.FormDefinition form = adapter.discover(job);
        AtsAdapter.PreparedApplication prepared = prepare(form, application);
        List<String> missing = prepared.missingRequired(form);
        Eligibility eligibility = baseEligibility(job, score, application, Optional.of(adapter), missing);
        List<PreviewField> fields = prepared.fields().stream()
                .map(field -> new PreviewField(field.name(), field.present(), field.source())).toList();
        List<PreviewUpload> uploads = prepared.uploads().stream()
                .map(upload -> new PreviewUpload(upload.documentType(), true, upload.contentType(), upload.contentLength())).toList();
        return new DryRunPreview(application.getId(), adapter.name(), form.postingId(), form.formTokenPresent(), fields, uploads,
                missing, eligibility.eligible(), eligibility.reason(), "All personal values and tokens are redacted; no request was submitted.");
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> list() {
        return apps.findAll().stream().map(application -> response(application, application.getJob(), scoring.findOrScore(application.getJob()))).toList();
    }

    private AtsAdapter.PreparedApplication prepare(AtsAdapter.FormDefinition form, Application application) {
        ParsedResume master = resumes.activeMasterEntity().map(resumes::parsed).orElse(null);
        ResumeContact contact = master == null ? new ResumeContact(null, null, null, null) : master.contact();
        List<AtsAdapter.PreparedField> fields = new ArrayList<>();
        List<AtsAdapter.PreparedUpload> uploads = new ArrayList<>();
        for (AtsAdapter.FormField field : form.fields()) {
            String key = (field.path() + " " + field.name() + " " + field.type()).toLowerCase(Locale.ROOT);
            boolean present = false;
            String source = "manual answer required";
            if (key.contains("name") && !key.contains("company")) { present = master != null && notBlank(master.name()); source = "master resume identity"; }
            else if (key.contains("email")) { present = notBlank(contact.email()); source = "master resume contact"; }
            else if (key.contains("phone")) { present = notBlank(contact.phone()); source = "master resume contact"; }
            else if (key.contains("linkedin") || key.contains("social")) { present = notBlank(contact.linkedin()); source = "master resume contact"; }
            else if (key.contains("resume") && key.contains("file")) {
                present = application.getResumeVersionId() != null;
                source = "tailored resume document";
                if (present) {
                    ResumeDetailsResponse resume = resumes.get(application.getResumeVersionId());
                    uploads.add(new AtsAdapter.PreparedUpload(field.path(), "Resume", resume.filename(), resume.contentType(), resume.fileSize(), resume.checksum()));
                }
            } else if ((key.contains("cover") || key.contains("letter")) && key.contains("file")) {
                present = application.getCoverLetterId() != null;
                source = "tailored cover letter document";
                if (present) {
                    CoverLetterService.LetterResponse letter = letters.get(application.getCoverLetterId());
                    ResumeDownload download = letters.download(application.getCoverLetterId());
                    uploads.add(new AtsAdapter.PreparedUpload(field.path(), "Cover Letter", letter.fileName(), download.contentType(), download.content().length, sha256(download.content())));
                }
            }
            fields.add(new AtsAdapter.PreparedField(field.path(), field.name(), present, source));
        }
        return new AtsAdapter.PreparedApplication(fields, uploads, form.formTokenPresent());
    }

    private Eligibility baseEligibility(Job job, JobScore score, Application application, Optional<AtsAdapter> adapter, List<String> missingAnswers) {
        List<String> reasons = new ArrayList<>();
        if (application.getStatus() != ApplicationStatus.READY_TO_APPLY) reasons.add("application is not READY_TO_APPLY");
        if (score.getScore() == null || score.getScore().compareTo(minimumScore) < 0) reasons.add("score below " + minimumScore.stripTrailingZeros().toPlainString());
        String location = (String.valueOf(job.getLocation()) + " " + String.valueOf(job.getCountry())).toLowerCase(Locale.ROOT);
        if (!(location.contains("uae") || location.contains("united arab emirates") || location.contains("dubai") || location.contains("abu dhabi") || location.contains("sharjah")))
            reasons.add("location is not explicitly UAE");
        if (adapter.isEmpty()) reasons.add("ATS is unsupported");
        if (resumes.activeMasterEntity().isEmpty()) reasons.add("no active master resume");
        if (application.getResumeVersionId() == null || application.getCoverLetterId() == null) reasons.add("document package is incomplete");
        if (!missingAnswers.isEmpty()) reasons.add("mandatory answers are incomplete: " + String.join(", ", missingAnswers));
        if (!allowlist.contains(host(job.getJobUrl()))) reasons.add("domain is not allowlisted");
        if (application.getStatus() == ApplicationStatus.AUTO_APPLIED || application.getStatus() == ApplicationStatus.MANUALLY_APPLIED
                || application.getSubmittedAt() != null || application.getSubmissionAttemptedAt() != null || application.getConfirmationId() != null)
            reasons.add("job was already submitted or attempted");
        OffsetDateTime start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
        if (apps.countByStatusAndAppliedAtBetween(ApplicationStatus.AUTO_APPLIED, start, start.plusDays(1)) >= dailyLimit)
            reasons.add("daily real-submission limit reached");
        return new Eligibility(reasons.isEmpty(), String.join("; ", reasons));
    }

    private WorkflowResponse pending(Application application, Job job, JobScore score, String reason) {
        application.setStatus(ApplicationStatus.PENDING_REVIEW);
        job.setStatus(ApplicationStatus.PENDING_REVIEW.name());
        application.setErrorMessage(reason);
        return response(apps.save(application), job, score);
    }

    private WorkflowResponse response(Application application, Job job, JobScore score) {
        String company = companies.findById(job.getCompanyId()).map(value -> value.getName()).orElse("Unknown company");
        Optional<AtsAdapter> adapter = adapters.resolve(job.getJobUrl());
        List<String> required = adapter.map(value -> value.requiredFields(job).stream().filter(AtsAdapter.FormField::required).map(AtsAdapter.FormField::name).toList()).orElse(List.of());
        Set<String> verified = required.isEmpty() ? Set.of() : verifiedApplicationAnswers(application);
        List<String> missing = required.stream().filter(field -> !verified.contains(field)).toList();
        return new WorkflowResponse(application.getId(), job.getId(), job.getTitle(), company, job.getJobUrl(), score.getScore(), confidence(score),
                csv(score.getMatchedKeywords()), csv(score.getMissingKeywords()), score.getLocationScore(), score.getExperienceScore(), score.getScoringReason(),
                required, missing, application.getStatus(), application.getResumeVersionId(), application.getCoverLetterId(), application.getAdapter(),
                application.getConfirmationId(), application.getConfirmationUrl(), application.getErrorMessage() != null ? application.getErrorMessage() : application.getNotes(),
                application.getAppliedAt(), application.getCreatedAt());
    }

    private Set<String> verifiedApplicationAnswers(Application application) {
        Set<String> verified = new HashSet<>();
        resumes.activeMasterEntity().ifPresent(masterEntity -> {
            ParsedResume parsed = resumes.parsed(masterEntity);
            if (parsed != null) {
                if (notBlank(parsed.name())) verified.add("Name");
                if (notBlank(parsed.contact().email())) verified.add("Email");
                if (notBlank(parsed.contact().phone())) verified.add("Phone");
            }
        });
        if (application.getResumeVersionId() != null) verified.add("Resume");
        return verified;
    }

    private Application required(UUID id) { return apps.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application not found")); }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static String confidence(JobScore score) {
        if (score.getScoringConfidence() != null) return score.getScoringConfidence();
        String reason = score.getScoringReason();
        return reason != null && reason.contains("confidence HIGH") ? "HIGH" : "LOW";
    }
    private static List<String> csv(String value) { return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList(); }
    private static String host(String url) { try { return Optional.ofNullable(URI.create(url).getHost()).orElse("").toLowerCase(Locale.ROOT); } catch (Exception exception) { return ""; } }
    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private record Eligibility(boolean eligible, String reason) { }
    public record PreviewField(String name, boolean present, String source) { }
    public record PreviewUpload(String documentType, boolean ready, String contentType, long contentLength) { }
    public record DryRunPreview(UUID applicationId, String adapter, String postingId, boolean formTokenPresent,
                                List<PreviewField> fields, List<PreviewUpload> uploads, List<String> missingRequiredAnswers,
                                boolean eligible, String eligibilityReason, String safetyNotice) { }
    public record WorkflowResponse(UUID id, UUID jobId, String job, String company, String sourceUrl, BigDecimal score, String confidence,
                                   List<String> matchedSkills, List<String> missingSkills, BigDecimal locationFit, BigDecimal experienceFit,
                                   String scoreExplanation, List<String> requiredFields, List<String> missingRequiredAnswers, ApplicationStatus status,
                                   UUID resumeVersionId, UUID coverLetterId, String adapter, String confirmationId, String confirmationUrl,
                                   String reason, OffsetDateTime appliedAt, OffsetDateTime createdAt) { }
}
