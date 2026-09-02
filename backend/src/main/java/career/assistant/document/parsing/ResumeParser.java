package career.assistant.document.parsing;

import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ParsedResumeDocument;
import career.assistant.document.model.ResumeContact;
import career.assistant.document.validation.ResumeFileValidator;
import career.assistant.document.validation.ResumeValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResumeParser {

    static final Pattern DATE_RANGE = Pattern.compile(
            "(?i)(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?(?:19|20)\\d{2}\\s*(?:-|–|—|to)\\s*(?:(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?(?:19|20)\\d{2}|present|current|now)"
    );
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<![\\d\\w])(?:\\+?\\d[\\d ()-]{7,}\\d)(?![\\d\\w])");
    private static final Pattern LINKEDIN = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?linkedin\\.com/(?:in|pub)/[A-Z0-9_%./-]+");
    private static final Pattern GITHUB = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?github\\.com/[A-Z0-9._-]+(?:/[A-Z0-9._-]+)?");
    private static final Pattern LOCATION = Pattern.compile("(?i)\\blocation\\s*[:|-]\\s*([^|•]+)");
    private static final Set<String> SUMMARY_HEADINGS = Set.of("summary", "professional summary", "profile", "professional profile");
    private static final Set<String> EXPERIENCE_HEADINGS = Set.of("experience", "professional experience", "work experience", "employment history");
    private static final Set<String> SKILLS_HEADINGS = Set.of("skills", "technical skills", "core competencies", "technologies");
    private static final Set<String> CERTIFICATION_HEADINGS = Set.of("certification", "certifications", "licenses & certifications", "licenses and certifications");
    private static final Set<String> EDUCATION_HEADINGS = Set.of("education", "academic background", "qualifications");
    private static final Set<String> ACHIEVEMENT_HEADINGS = Set.of("achievements", "accomplishments", "awards", "key achievements");

    public ParsedResumeDocument parse(byte[] content, String contentType) {
        String text = extractText(content, contentType).replace("\u0000", "").replace("\r\n", "\n").trim();
        if (text.isBlank()) throw new ResumeValidationException("Resume contains no extractable text");
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
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
                    StringBuilder text = new StringBuilder();
                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        String value = paragraph.getText().trim();
                        if (!value.isBlank()) {
                            if (paragraph.getNumID() != null) text.append("• ");
                            text.append(value).append('\n');
                        }
                    }
                    return text.toString();
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new ResumeValidationException("Resume content could not be parsed", exception);
        }
        throw new ResumeValidationException("Unsupported resume content type");
    }

    ParsedResume structure(String text) {
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(String::trim).filter(line -> !line.isBlank()).toList();
        String name = firstIdentityLine(lines);
        ResumeContact contact = extractContact(lines);

        Map<Section, List<String>> sections = new LinkedHashMap<>();
        for (Section value : Section.values()) sections.put(value, new ArrayList<>());
        Section section = Section.NONE;
        for (String line : lines) {
            Section next = heading(line);
            if (next != Section.NONE) section = next;
            else sections.get(section).add(line);
        }

        LinkedHashMap<String, String> skills = new LinkedHashMap<>();
        SkillCatalog.findMentionedSkills(text).forEach(value -> skills.putIfAbsent(normalize(value), value));
        sections.get(Section.SKILLS).stream()
                .flatMap(line -> Arrays.stream(line.split("[,;|•()]+")))
                .map(this::cleanSkill)
                .filter(value -> !value.isBlank() && value.length() <= 80 && !value.contains(":"))
                .forEach(value -> skills.putIfAbsent(normalize(value), value));
        if (skills.containsKey("github actions")) skills.remove("github");
        if (skills.containsKey("amazon cloudwatch")) skills.remove("cloudwatch");

        return new ParsedResume(
                name,
                contact,
                joinSentences(sections.get(Section.SUMMARY)),
                parseExperience(sections.get(Section.EXPERIENCE)),
                List.copyOf(skills.values()),
                distinct(sections.get(Section.CERTIFICATIONS)),
                distinct(sections.get(Section.EDUCATION)),
                distinct(sections.get(Section.ACHIEVEMENTS))
        );
    }

    private ResumeContact extractContact(List<String> lines) {
        String email = firstMatch(lines, EMAIL);
        String phone = firstMatch(lines, PHONE);
        String linkedin = firstMatch(lines, LINKEDIN);
        String github = firstMatch(lines, GITHUB);
        String location = firstMatch(lines, LOCATION);

        // Contact rows are commonly rendered as icon-prefixed, pipe-separated text.
        // Inspect only segments on a row that already contains a deterministic contact
        // marker; never derive contact data from the authenticated account.
        if (location == null) {
            for (String line : lines) {
                if (!line.contains("|") || (!EMAIL.matcher(line).find() && !PHONE.matcher(line).find()
                        && !LINKEDIN.matcher(line).find() && !GITHUB.matcher(line).find())) continue;
                for (String segment : line.split("\\|")) {
                    String candidate = cleanContact(segment);
                    if (candidate.contains(",") && candidate.length() <= 100 && !EMAIL.matcher(candidate).find()
                            && !PHONE.matcher(candidate).find() && !LINKEDIN.matcher(candidate).find()
                            && !GITHUB.matcher(candidate).find()) {
                        location = candidate;
                        break;
                    }
                }
                if (location != null) break;
            }
        }
        return new ResumeContact(email, phone, linkedin, github, location);
    }

    private String firstIdentityLine(List<String> lines) {
        for (String line : lines) {
            if (heading(line) != Section.NONE) break;
            String candidate = line.replaceAll("\\s*[|•].*$", "").trim();
            if (!EMAIL.matcher(candidate).find() && !PHONE.matcher(candidate).find() && !LINKEDIN.matcher(candidate).find()
                    && candidate.length() <= 80 && candidate.matches("[\\p{L} .'-]{3,80}")
                    && candidate.trim().split("\\s+").length >= 2 && candidate.trim().split("\\s+").length <= 6) return candidate;
        }
        return null;
    }

    private String firstMatch(List<String> lines, Pattern pattern) {
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) return cleanContact(matcher.groupCount() == 0 ? matcher.group() : matcher.group(1));
        }
        return null;
    }

    private List<ExperienceEntry> parseExperience(List<String> rawLines) {
        List<String> lines = rawLines.stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        List<DateMarker> markers = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = DATE_RANGE.matcher(lines.get(i));
            if (matcher.find()) markers.add(new DateMarker(i, matcher.group().trim(), cleanHeader(lines.get(i).substring(0, matcher.start()))));
        }
        if (markers.isEmpty()) return List.of();

        List<Integer> headerStarts = new ArrayList<>();
        for (int i = 0; i < markers.size(); i++) {
            DateMarker marker = markers.get(i);
            int lowerBound = i == 0 ? 0 : markers.get(i - 1).lineIndex() + 1;
            int start = marker.lineIndex();
            int needed = marker.inlineHeader().isBlank() ? 2 : 1;
            while (start > lowerBound && marker.lineIndex() - start < needed && looksLikeHeader(lines.get(start - 1))) start--;
            headerStarts.add(start);
        }

        List<ExperienceEntry> entries = new ArrayList<>();
        for (int i = 0; i < markers.size(); i++) {
            DateMarker marker = markers.get(i);
            List<String> header = new ArrayList<>();
            for (int line = headerStarts.get(i); line < marker.lineIndex(); line++) header.add(cleanHeader(lines.get(line)));
            if (!marker.inlineHeader().isBlank()) header.add(marker.inlineHeader());
            Header parsedHeader = parseHeader(header);
            int highlightsEnd = i + 1 < markers.size() ? headerStarts.get(i + 1) : lines.size();
            List<String> highlights = normalizeHighlights(lines.subList(marker.lineIndex() + 1, highlightsEnd));
            entries.add(new ExperienceEntry(parsedHeader.employer(), parsedHeader.title(), marker.dates(), highlights));
        }
        return List.copyOf(entries);
    }

    private Header parseHeader(List<String> values) {
        List<String> parts = values.stream().filter(value -> !value.isBlank())
                .flatMap(value -> Arrays.stream(value.split("\\s+[|]\\s+|\\s+at\\s+", 3)))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        if (parts.size() == 2) return new Header(parts.get(1), parts.get(0));
        return new Header(null, null);
    }

    private List<String> normalizeHighlights(List<String> values) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String original : values) {
            boolean explicitBullet = original.matches("^[•▪◦*+-].*");
            String line = cleanBullet(original);
            if (line.isBlank()) continue;
            if (explicitBullet && !current.isEmpty()) flushHighlight(result, current);
            if (!current.isEmpty()) current.append(' ');
            current.append(line);
            if (endsSentence(line)) flushHighlight(result, current);
        }
        flushHighlight(result, current);
        return List.copyOf(result);
    }

    private void flushHighlight(List<String> result, StringBuilder value) {
        if (!value.isEmpty()) result.add(value.toString().replaceAll("\\s+", " ").trim());
        value.setLength(0);
    }

    private boolean endsSentence(String value) { return value.matches(".*[.!?;:]$") || value.matches(".*\\d%$"); }
    private boolean looksLikeHeader(String value) {
        String clean = cleanHeader(value);
        return !value.matches("^[•▪◦*+-].*") && clean.length() <= 100 && clean.split("\\s+").length <= 12
                && !endsSentence(clean) && !EMAIL.matcher(clean).find() && !PHONE.matcher(clean).find();
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

    private String cleanBullet(String line) { return line.replaceFirst("^[•▪◦*+-]\\s*", "").trim(); }
    private String cleanHeader(String line) { return cleanBullet(line).replaceAll("^[|,–—-]+|[|,–—-]+$", "").trim(); }
    private String cleanSkill(String value) { return cleanBullet(value).replaceFirst("^[^:]{1,30}:\\s*", "").trim(); }
    private String cleanContact(String value) {
        if (value == null) return null;
        return value.replaceFirst("^[^\\p{L}\\p{N}+@]+", "")
                .replaceAll("[|•,;]+$", "").trim();
    }
    private String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }
    private String joinSentences(List<String> values) { return values.isEmpty() ? null : String.join(" ", values).replaceAll("\\s+", " ").trim(); }
    private List<String> distinct(List<String> values) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        values.stream().map(this::cleanBullet).filter(value -> !value.isBlank()).forEach(value -> result.putIfAbsent(normalize(value), value));
        return List.copyOf(result.values());
    }

    private enum Section { NONE, SUMMARY, EXPERIENCE, SKILLS, CERTIFICATIONS, EDUCATION, ACHIEVEMENTS }
    private record DateMarker(int lineIndex, String dates, String inlineHeader) { }
    private record Header(String employer, String title) { }
}
