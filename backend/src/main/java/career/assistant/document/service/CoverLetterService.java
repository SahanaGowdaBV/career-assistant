package career.assistant.document.service;

import career.assistant.api.ResourceNotFoundException;
import career.assistant.company.repository.CompanyRepository;
import career.assistant.document.entity.CoverLetter;
import career.assistant.document.entity.ResumeVersion;
import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ResumeContact;
import career.assistant.document.model.ResumeDownload;
import career.assistant.document.quality.DocumentQualityGate;
import career.assistant.document.repository.CoverLetterRepository;
import career.assistant.document.repository.ResumeVersionRepository;
import career.assistant.document.storage.ResumeStorage;
import career.assistant.document.storage.StoredResumeObject;
import career.assistant.job.entity.Job;
import career.assistant.jobscore.entity.JobScore;
import career.assistant.jobscore.repository.JobScoreRepository;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CoverLetterService {
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String FONT = "Arial";
    private final CoverLetterRepository letters;
    private final ResumeVersionRepository resumes;
    private final JobScoreRepository scores;
    private final CompanyRepository companies;
    private final ResumeJsonCodec json;
    private final ResumeStorage storage;
    private final DocumentQualityGate qualityGate;

    public CoverLetterService(
            CoverLetterRepository letters,
            ResumeVersionRepository resumes,
            JobScoreRepository scores,
            CompanyRepository companies,
            ResumeJsonCodec json,
            ResumeStorage storage,
            DocumentQualityGate qualityGate
    ) {
        this.letters = letters;
        this.resumes = resumes;
        this.scores = scores;
        this.companies = companies;
        this.json = json;
        this.storage = storage;
        this.qualityGate = qualityGate;
    }

    @Transactional
    public CoverLetter generate(Job job) {
        return letters.findFirstByJobIdOrderByCreatedAtDesc(job.getId()).orElseGet(() -> generateVersion(job));
    }

    @Transactional
    public CoverLetter generateNewVersion(Job job) {
        return generateVersion(job);
    }

    private CoverLetter generateVersion(Job job) {
        ResumeVersion master = resumes.findFirstByMasterResumeTrue()
                .orElseThrow(() -> new ResumeConflictException("An active master resume is required"));
        ParsedResume parsed = json.readResume(master.getStructuredExperience());
        JobScore score = scores.findByJob(job)
                .orElseThrow(() -> new ResumeConflictException("The job must be scored before package generation"));
        String company = companies.findById(job.getCompanyId()).map(value -> value.getName())
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> new ResumeConflictException("The job company is required for cover-letter generation"));
        if (job.getTitle() == null || job.getTitle().isBlank()) throw new ResumeConflictException("The job title is required for cover-letter generation");

        List<String> matched = matchedSkills(parsed, score);
        CoverLetterDraft draft = draft(parsed, job, company, matched);
        qualityGate.validateCoverLetter(parsed, job.getTitle(), company, draft.content(), draft.sourceClaims());
        byte[] bytes = docx(parsed, job.getTitle(), company, draft.content());
        String path = OffsetDateTime.now(ZoneOffset.UTC).getYear() + "/cover-letters/" + UUID.randomUUID() + ".docx";
        CoverLetter letter = new CoverLetter();
        letter.setJobId(job.getId());
        letter.setTitle(job.getTitle() + " cover letter");
        letter.setContent(draft.content());
        letter.setFileName(safe(job.getTitle()) + "-cover-letter.docx");
        letter.setStoragePath(path);
        letter.setContentType(DOCX);
        letter.setCustomized(true);
        letter.setCustomizationSummary("Role-specific letter grounded in master-resume experience and matched skills: " + String.join(", ", matched));
        storage.store(path, bytes, DOCX);
        try {
            return letters.save(letter);
        } catch (RuntimeException exception) {
            storage.delete(path);
            throw exception;
        }
    }

    private List<String> matchedSkills(ParsedResume parsed, JobScore score) {
        Set<String> source = new LinkedHashSet<>(parsed.skills());
        return csv(score.getMatchedKeywords()).stream().filter(source::contains).distinct().toList();
    }

    private CoverLetterDraft draft(ParsedResume resume, Job job, String company, List<String> matched) {
        List<ExperienceEntry> ranked = resume.experience().stream()
                .sorted(Comparator.comparingInt((ExperienceEntry entry) -> relevance(entry, matched)).reversed())
                .toList();
        List<ExperienceEntry> selected = ranked.stream().filter(entry -> relevance(entry, matched) > 0).limit(3).toList();
        if (selected.isEmpty() && !ranked.isEmpty()) selected = ranked.stream().limit(2).toList();

        List<String> claims = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(company).append(" Hiring Team,\n\n");
        body.append("I am writing to apply for the ").append(job.getTitle()).append(" role at ").append(company)
                .append(". My application is based on the employment history and capabilities presented in my resume, with emphasis on the areas that most directly overlap with this position.");
        if (!matched.isEmpty()) {
            body.append(" The clearest skills overlap is ").append(humanList(matched)).append(".");
            claims.addAll(matched);
        }
        body.append("\n\n");

        if (resume.professionalSummary() != null && !resume.professionalSummary().isBlank()) {
            body.append(resume.professionalSummary()).append(" ");
            claims.add(resume.professionalSummary());
        }
        body.append("The following experience provides the most relevant evidence for this application.");

        for (ExperienceEntry entry : selected) {
            body.append("\n\nIn my employment history, ").append(entry.jobTitle()).append(" at ").append(entry.employer())
                    .append(" is recorded for ").append(entry.employmentDates()).append(". ");
            claims.add(entry.jobTitle()); claims.add(entry.employer()); claims.add(entry.employmentDates());
            List<String> highlights = entry.highlights().stream()
                    .sorted(Comparator.comparingInt((String value) -> relevance(value, matched)).reversed())
                    .limit(2).toList();
            for (String highlight : highlights) {
                body.append(highlight).append(' ');
                claims.add(highlight);
            }
        }

        body.append("\n\nThese examples are the practical basis for my fit with the role. The attached resume supplies the full chronology, scope, and context. I have focused this letter on work already represented there and on the genuine skills overlap identified for this opening, without presenting an area absent from my background as experience.");
        body.append(" That approach would let our conversation stay concrete: the responsibilities of the opening can be considered alongside the relevant work above, while the complete resume remains the source for the details of my employment and capabilities. It also keeps a clear boundary between demonstrated experience and role requirements.");
        body.append("\n\nThank you for considering my application. I would be glad to discuss the experience above and how it relates to the ")
                .append(job.getTitle()).append(" position.\n\nSincerely,\n").append(resume.name());

        String content = body.toString();
        while (wordCount(content) > 350 && selected.size() > 1) {
            selected = selected.subList(0, selected.size() - 1);
            return draftWithSelected(resume, job, company, matched, selected);
        }
        return new CoverLetterDraft(content, List.copyOf(claims));
    }

    private CoverLetterDraft draftWithSelected(ParsedResume resume, Job job, String company, List<String> matched, List<ExperienceEntry> selected) {
        List<String> claims = new ArrayList<>();
        String skillText = matched.isEmpty() ? "the experience detailed below" : humanList(matched);
        claims.addAll(matched);
        StringBuilder body = new StringBuilder("Dear ").append(company).append(" Hiring Team,\n\n")
                .append("I am writing to apply for the ").append(job.getTitle()).append(" role at ").append(company)
                .append(". My application is grounded in the employment history presented in my resume. The most relevant overlap for this position is ").append(skillText).append(".\n\n");
        if (resume.professionalSummary() != null && !resume.professionalSummary().isBlank()) { body.append(resume.professionalSummary()).append("\n\n"); claims.add(resume.professionalSummary()); }
        for (ExperienceEntry entry : selected) {
            body.append("My resume records ").append(entry.jobTitle()).append(" at ").append(entry.employer()).append(" for ").append(entry.employmentDates()).append(". ");
            claims.add(entry.jobTitle()); claims.add(entry.employer()); claims.add(entry.employmentDates());
            for (String highlight : entry.highlights().stream().limit(2).toList()) { body.append(highlight).append(' '); claims.add(highlight); }
            body.append("\n\n");
        }
        body.append("These examples provide a concrete basis for my fit with the role. I have concentrated this letter on experience relevant to the opening and have left the full employment sequence and supporting detail in the resume for review. That keeps the connection to the position specific to work already represented in my background and gives us a clear basis for discussing the responsibilities of the opening. It also keeps a clear boundary between demonstrated experience and role requirements.\n\n")
                .append("Thank you for considering my application. I would be glad to discuss how this background relates to the ").append(job.getTitle()).append(" position.\n\nSincerely,\n").append(resume.name());
        return new CoverLetterDraft(body.toString(), List.copyOf(claims));
    }

    private int relevance(ExperienceEntry entry, List<String> matched) {
        return relevance(entry.jobTitle() + " " + entry.employer() + " " + String.join(" ", entry.highlights()), matched);
    }
    private int relevance(String value, List<String> matched) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return (int) matched.stream().filter(skill -> normalized.contains(skill.toLowerCase(Locale.ROOT))).count();
    }

    private byte[] docx(ParsedResume resume, String role, String company, String text) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var section = document.getDocument().getBody().addNewSectPr();
            var size = section.addNewPgSz(); size.setW(BigInteger.valueOf(12_240)); size.setH(BigInteger.valueOf(15_840));
            var margins = section.addNewPgMar();
            margins.setTop(BigInteger.valueOf(1_152)); margins.setBottom(BigInteger.valueOf(1_152));
            margins.setLeft(BigInteger.valueOf(1_440)); margins.setRight(BigInteger.valueOf(1_440));
            margins.setHeader(BigInteger.valueOf(708)); margins.setFooter(BigInteger.valueOf(708));
            XWPFParagraph name = paragraph(document, 2, 1.0);
            run(name, resume.name(), 16, true, "17365D");
            ResumeContact contact = resume.contact();
            XWPFParagraph details = paragraph(document, 12, 1.0);
            run(details, String.join(" | ", nonNull(contact.email(), contact.phone(), contact.linkedin(), contact.location())), 9.5, false, "555555");
            XWPFParagraph subject = paragraph(document, 14, 1.0);
            run(subject, role + " | " + company, 11, true, "17365D");
            for (String value : text.split("\\n", -1)) {
                if (value.isBlank()) continue;
                XWPFParagraph paragraph = paragraph(document, 8, 1.08);
                if (value.equals("Sincerely,") || value.equals(resume.name())) paragraph.setSpacingAfter(2 * 20);
                run(paragraph, value, 10.5, false, "222222");
            }
            document.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create cover letter", exception);
        }
    }

    private XWPFParagraph paragraph(XWPFDocument document, int after, double line) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingBefore(0); paragraph.setSpacingAfter(after * 20); paragraph.setSpacingBetween(line);
        return paragraph;
    }
    private void run(XWPFParagraph paragraph, String text, double size, boolean bold, String color) {
        XWPFRun run = paragraph.createRun(); run.setFontFamily(FONT); run.setFontSize(size); run.setBold(bold); run.setColor(color); run.setText(text == null ? "" : text);
    }

    @Transactional(readOnly = true) public List<LetterResponse> list() { return letters.findAllByOrderByCreatedAtDesc().stream().map(LetterResponse::of).toList(); }
    @Transactional(readOnly = true) public LetterResponse get(UUID id) { return LetterResponse.of(required(id)); }
    @Transactional(readOnly = true) public ResumeDownload download(UUID id) { CoverLetter letter = required(id); StoredResumeObject object = storage.load(letter.getStoragePath()); return new ResumeDownload(object.content(), letter.getContentType(), letter.getFileName()); }
    private CoverLetter required(UUID id) { return letters.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cover letter not found")); }
    private static List<String> csv(String value) { return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList(); }
    private static String safe(String value) { return value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-|-$", ""); }
    private static String humanList(List<String> values) { return values.size() < 2 ? String.join("", values) : String.join(", ", values.subList(0, values.size() - 1)) + ", and " + values.getLast(); }
    private static int wordCount(String value) { return value.trim().split("\\s+").length; }
    private static List<String> nonNull(String... values) { return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).toList(); }
    private record CoverLetterDraft(String content, List<String> sourceClaims) { }
    public record LetterResponse(UUID id, UUID jobId, String title, String content, String fileName, String summary, OffsetDateTime createdAt) {
        static LetterResponse of(CoverLetter letter) { return new LetterResponse(letter.getId(), letter.getJobId(), letter.getTitle(), letter.getContent(), letter.getFileName(), letter.getCustomizationSummary(), letter.getCreatedAt()); }
    }
}
