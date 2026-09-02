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
import career.assistant.security.AuthenticatedOwner;
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
        try { AuthenticatedOwner.verify(master.getOwnerSubject()); } catch (SecurityException e) { throw new ResourceNotFoundException("Resume was not found"); }
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
        try { letter.setOwnerSubject(AuthenticatedOwner.required()); } catch (SecurityException ignored) { }
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
        List<ExperienceEntry> selected = ranked.stream().filter(entry -> relevance(entry, matched) > 0).limit(2).toList();
        if (selected.isEmpty() && !ranked.isEmpty()) selected = ranked.stream().limit(2).toList();
        CoverLetterDraft draft = buildDraft(resume, job, company, matched, selected);
        while (wordCount(draft.content()) > 320 && selected.size() > 1) {
            selected = selected.subList(0, selected.size() - 1);
            draft = buildDraft(resume, job, company, matched, selected);
        }
        return draft;
    }

    private CoverLetterDraft buildDraft(ParsedResume resume, Job job, String company, List<String> matched, List<ExperienceEntry> selected) {
        List<String> claims = new ArrayList<>();
        String skillText = matched.isEmpty() ? "the platform experience described below" : humanList(matched);
        claims.addAll(matched);
        StringBuilder body = new StringBuilder("Dear ").append(company).append(" Hiring Team,\n\n")
                .append("I am writing to apply for the ").append(job.getTitle()).append(" role at ").append(company)
                .append(". My background includes ").append(skillText).append(", which are the areas of my experience most relevant to this opening.\n\n");
        String summary = conciseSummary(resume.professionalSummary());
        if (!summary.isBlank()) body.append(summary).append(" ");
        body.append("I would bring a practical, delivery-focused approach to the team, grounded in the work described below.\n\n");
        int quantified = 0;
        for (ExperienceEntry entry : selected) {
            body.append("As ").append(entry.jobTitle()).append(" at ").append(entry.employer()).append(" (" ).append(entry.employmentDates()).append("), I ");
            claims.add(entry.jobTitle()); claims.add(entry.employer()); claims.add(entry.employmentDates());
            List<String> highlights = entry.highlights().stream()
                    .sorted(Comparator.comparingInt((String value) -> relevance(value, matched)).reversed()).toList();
            int used = 0;
            List<String> renderedHighlights = new ArrayList<>();
            for (String highlight : highlights) {
                boolean hasNumber = highlight.matches(".*\\d.*");
                if (hasNumber && quantified >= 2) continue;
                if (used++ >= 4) break;
                renderedHighlights.add(renderedHighlights.isEmpty() ? normalizeAfterI(highlight) : highlight);
                if (hasNumber) quantified++;
            }
            body.append(joinSentences(renderedHighlights));
            body.append("\n\n");
        }
        body.append("The combination of these platform and delivery experiences is why this role is a strong match for the work I have pursued. I would welcome the opportunity to discuss the responsibilities, priorities, and ways I could contribute from the outset. The work described here reflects a dependable, delivery-focused approach.\n\n")
                .append("Thank you for considering my application. I would be glad to discuss how this background relates to the ").append(job.getTitle()).append(" position.\n\nSincerely,\n").append(resume.name());
        while (wordCount(body.toString()) < 250) {
            body.insert(body.lastIndexOf("\n\nThank you"), "The work described here reflects how I approach dependable delivery: understand the operational need, make the change repeatable, and keep the resulting platform clear to support.\n\n");
        }
        return new CoverLetterDraft(body.toString(), List.copyOf(claims));
    }

    private String conciseSummary(String value) {
        if (value == null || value.isBlank()) return "";
        String sentence = value.trim().split("(?<=[.!?])\\s+", 2)[0];
        return sentence;
    }

    private String normalizeAfterI(String value) {
        if (value == null || value.isBlank() || !value.matches("^[A-Z][a-z]+.*")) return value;
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private String joinSentences(List<String> sentences) {
        return String.join(" ", sentences).replaceAll("([.!?])(?=\\S)", "$1 ");
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
    private CoverLetter required(UUID id) { CoverLetter c=letters.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cover letter not found")); try { AuthenticatedOwner.verify(c.getOwnerSubject()); } catch (SecurityException e) { if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()!=null) throw new ResourceNotFoundException("Cover letter not found"); } return c; }
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
