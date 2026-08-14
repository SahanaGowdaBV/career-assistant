package career.assistant.document.parsing;

import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ParsedResumeDocument;
import career.assistant.document.validation.ResumeFileValidator;
import career.assistant.document.validation.ResumeValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResumeParser {

    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?i)(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?(?:19|20)\\d{2}\\s*(?:-|–|—|to)\\s*(?:(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?(?:19|20)\\d{2}|present|current|now)"
    );
    private static final Set<String> SUMMARY_HEADINGS = Set.of("summary", "professional summary", "profile", "professional profile");
    private static final Set<String> EXPERIENCE_HEADINGS = Set.of("experience", "professional experience", "work experience", "employment history");
    private static final Set<String> SKILLS_HEADINGS = Set.of("skills", "technical skills", "core competencies", "technologies");
    private static final Set<String> CERTIFICATION_HEADINGS = Set.of("certification", "certifications", "licenses & certifications", "licenses and certifications");
    private static final Set<String> EDUCATION_HEADINGS = Set.of("education", "academic background", "qualifications");
    private static final Set<String> ACHIEVEMENT_HEADINGS = Set.of("achievements", "accomplishments", "awards", "key achievements");

    public ParsedResumeDocument parse(byte[] content, String contentType) {
        String text = extractText(content, contentType).replace("\u0000", "").replace("\r\n", "\n").trim();
        if (text.isBlank()) {
            throw new ResumeValidationException("Resume contains no extractable text");
        }
        return new ParsedResumeDocument(text, structure(text));
    }

    private String extractText(byte[] content, String contentType) {
        try {
            if (ResumeFileValidator.PDF.equals(contentType)) {
                try (PDDocument document = Loader.loadPDF(content)) {
                    return new PDFTextStripper().getText(document);
                }
            }
            if (ResumeFileValidator.DOCX.equals(contentType)) {
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new ResumeValidationException("Resume content could not be parsed", exception);
        }
        throw new ResumeValidationException("Unsupported resume content type");
    }

    ParsedResume structure(String text) {
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();

        String name = lines.stream()
                .filter(line -> heading(line) == Section.NONE)
                .filter(this::looksLikeName)
                .findFirst()
                .orElse(null);

        List<String> summary = new ArrayList<>();
        List<String> experience = new ArrayList<>();
        List<String> skills = new ArrayList<>();
        List<String> certifications = new ArrayList<>();
        List<String> education = new ArrayList<>();
        List<String> achievements = new ArrayList<>();
        Section section = Section.NONE;

        for (String line : lines) {
            Section next = heading(line);
            if (next != Section.NONE) {
                section = next;
                continue;
            }
            switch (section) {
                case SUMMARY -> summary.add(line);
                case EXPERIENCE -> experience.add(line);
                case SKILLS -> skills.add(line);
                case CERTIFICATIONS -> certifications.add(cleanBullet(line));
                case EDUCATION -> education.add(cleanBullet(line));
                case ACHIEVEMENTS -> achievements.add(cleanBullet(line));
                default -> { }
            }
        }

        LinkedHashSet<String> structuredSkills = new LinkedHashSet<>(SkillCatalog.findMentionedSkills(text));
        skills.stream()
                .flatMap(line -> Arrays.stream(line.split("[,;|•]")))
                .map(this::cleanSkill)
                .filter(value -> !value.isBlank() && value.length() <= 80)
                .forEach(structuredSkills::add);

        return new ParsedResume(
                name,
                summary.isEmpty() ? null : String.join(" ", summary),
                parseExperience(experience),
                List.copyOf(structuredSkills),
                distinct(certifications),
                distinct(education),
                distinct(achievements)
        );
    }

    private List<ExperienceEntry> parseExperience(List<String> lines) {
        List<ExperienceEntry> entries = new ArrayList<>();
        List<String> pendingHeader = new ArrayList<>();
        ExperienceBuilder current = null;

        for (String original : lines) {
            String line = cleanBullet(original);
            Matcher dates = DATE_RANGE.matcher(line);
            if (dates.find()) {
                if (current != null) {
                    entries.add(current.build());
                }
                String dateText = dates.group().trim();
                String beforeDates = line.substring(0, dates.start()).trim().replaceAll("[|,–—-]+$", "").trim();
                List<String> header = new ArrayList<>(pendingHeader);
                if (!beforeDates.isBlank()) {
                    header.add(beforeDates);
                }
                pendingHeader.clear();
                current = ExperienceBuilder.from(header, dateText);
            } else if (current == null) {
                pendingHeader.add(line);
                if (pendingHeader.size() > 3) {
                    pendingHeader.removeFirst();
                }
            } else if (looksLikeHighlight(original)) {
                current.highlights.add(line);
            } else if (current.employer == null || current.jobTitle == null) {
                current.fillHeader(line);
            } else {
                current.highlights.add(line);
            }
        }
        if (current != null) {
            entries.add(current.build());
        }
        return List.copyOf(entries);
    }

    private Section heading(String line) {
        String normalized = line.toLowerCase(Locale.ROOT).replaceFirst("[:：]$", "").trim();
        if (SUMMARY_HEADINGS.contains(normalized)) return Section.SUMMARY;
        if (EXPERIENCE_HEADINGS.contains(normalized)) return Section.EXPERIENCE;
        if (SKILLS_HEADINGS.contains(normalized)) return Section.SKILLS;
        if (CERTIFICATION_HEADINGS.contains(normalized)) return Section.CERTIFICATIONS;
        if (EDUCATION_HEADINGS.contains(normalized)) return Section.EDUCATION;
        if (ACHIEVEMENT_HEADINGS.contains(normalized)) return Section.ACHIEVEMENTS;
        return Section.NONE;
    }

    private boolean looksLikeName(String line) {
        return line.length() <= 80 && line.matches("[\\p{L} .'-]{3,80}")
                && line.trim().split("\\s+").length >= 2
                && line.trim().split("\\s+").length <= 6;
    }

    private boolean looksLikeHighlight(String line) {
        return line.matches("^[•▪◦*+-].*");
    }

    private String cleanBullet(String line) {
        return line.replaceFirst("^[•▪◦*+-]\\s*", "").trim();
    }

    private String cleanSkill(String value) {
        return cleanBullet(value).replaceFirst("^[^:]{1,30}:\\s*", "").trim();
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values.stream().filter(value -> !value.isBlank()).toList()));
    }

    private enum Section { NONE, SUMMARY, EXPERIENCE, SKILLS, CERTIFICATIONS, EDUCATION, ACHIEVEMENTS }

    private static final class ExperienceBuilder {
        private String employer;
        private String jobTitle;
        private final String dates;
        private final List<String> highlights = new ArrayList<>();

        private ExperienceBuilder(String employer, String jobTitle, String dates) {
            this.employer = employer;
            this.jobTitle = jobTitle;
            this.dates = dates;
        }

        static ExperienceBuilder from(List<String> headerLines, String dates) {
            List<String> parts = headerLines.stream()
                    .flatMap(line -> Arrays.stream(line.split("\\s+[|]\\s+|\\s+at\\s+", 3)))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
            if (parts.size() >= 2) {
                return new ExperienceBuilder(parts.get(1), parts.get(0), dates);
            }
            return new ExperienceBuilder(parts.isEmpty() ? null : parts.get(0), null, dates);
        }

        void fillHeader(String line) {
            if (employer == null) employer = line;
            else if (jobTitle == null) jobTitle = line;
        }

        ExperienceEntry build() {
            return new ExperienceEntry(employer, jobTitle, dates, highlights);
        }
    }
}
