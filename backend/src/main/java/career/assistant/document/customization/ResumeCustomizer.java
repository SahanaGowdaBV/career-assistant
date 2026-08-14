package career.assistant.document.customization;

import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ResumeCustomizer {

    public CustomizedResumeDocument customize(ParsedResume master, String jobText) {
        Set<String> jobTokens = tokens(jobText);
        List<String> skills = master.skills().stream()
                .sorted(Comparator.comparingInt((String value) -> relevance(value, jobTokens)).reversed())
                .toList();
        List<ExperienceEntry> experience = master.experience().stream()
                .map(entry -> new ExperienceEntry(
                        entry.employer(),
                        entry.jobTitle(),
                        entry.employmentDates(),
                        entry.highlights().stream()
                                .sorted(Comparator.comparingInt((String value) -> relevance(value, jobTokens)).reversed())
                                .toList()
                ))
                .sorted(Comparator.comparingInt((ExperienceEntry entry) -> experienceRelevance(entry, jobTokens)).reversed())
                .toList();
        List<String> achievements = master.achievements().stream()
                .filter(value -> relevance(value, jobTokens) > 0)
                .toList();
        if (achievements.isEmpty()) {
            achievements = master.achievements();
        }

        ParsedResume customized = new ParsedResume(
                master.name(),
                master.professionalSummary(),
                experience,
                skills,
                master.certifications(),
                master.education(),
                achievements
        );
        String text = renderPlainText(customized);
        return new CustomizedResumeDocument(renderDocx(customized), text, customized,
                skills.stream().filter(skill -> relevance(skill, jobTokens) > 0).toList(), achievements);
    }

    private byte[] renderDocx(ParsedResume resume) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (resume.name() != null) {
                XWPFParagraph name = document.createParagraph();
                name.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun run = name.createRun();
                run.setBold(true);
                run.setFontSize(18);
                run.setText(resume.name());
            }
            if (resume.professionalSummary() != null) {
                heading(document, "Professional Summary");
                paragraph(document, resume.professionalSummary());
            }
            if (!resume.skills().isEmpty()) {
                heading(document, "Skills");
                paragraph(document, String.join(" • ", resume.skills()));
            }
            if (!resume.experience().isEmpty()) {
                heading(document, "Professional Experience");
                for (ExperienceEntry entry : resume.experience()) {
                    List<String> header = new ArrayList<>();
                    if (entry.jobTitle() != null) header.add(entry.jobTitle());
                    if (entry.employer() != null) header.add(entry.employer());
                    if (entry.employmentDates() != null) header.add(entry.employmentDates());
                    XWPFParagraph paragraph = document.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setBold(true);
                    run.setText(String.join(" | ", header));
                    entry.highlights().forEach(value -> bullet(document, value));
                }
            }
            section(document, "Certifications", resume.certifications());
            section(document, "Education", resume.education());
            section(document, "Achievements", resume.achievements());
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Customized resume document could not be generated", exception);
        }
    }

    private String renderPlainText(ParsedResume resume) {
        StringBuilder text = new StringBuilder();
        append(text, resume.name());
        appendSection(text, "Professional Summary", resume.professionalSummary() == null ? List.of() : List.of(resume.professionalSummary()));
        appendSection(text, "Skills", resume.skills());
        if (!resume.experience().isEmpty()) {
            text.append("Professional Experience\n");
            resume.experience().forEach(entry -> {
                append(text, String.join(" | ", nonNull(entry.jobTitle(), entry.employer(), entry.employmentDates())));
                entry.highlights().forEach(value -> append(text, value));
            });
        }
        appendSection(text, "Certifications", resume.certifications());
        appendSection(text, "Education", resume.education());
        appendSection(text, "Achievements", resume.achievements());
        return text.toString().trim();
    }

    private void section(XWPFDocument document, String title, List<String> values) {
        if (!values.isEmpty()) {
            heading(document, title);
            values.forEach(value -> bullet(document, value));
        }
    }

    private void heading(XWPFDocument document, String value) {
        XWPFRun run = document.createParagraph().createRun();
        run.setBold(true);
        run.setFontSize(13);
        run.setText(value);
    }

    private void paragraph(XWPFDocument document, String value) {
        document.createParagraph().createRun().setText(value);
    }

    private void bullet(XWPFDocument document, String value) {
        document.createParagraph().createRun().setText("• " + value);
    }

    private void appendSection(StringBuilder text, String title, List<String> values) {
        if (!values.isEmpty()) {
            text.append(title).append('\n');
            values.forEach(value -> append(text, value));
        }
    }

    private void append(StringBuilder text, String value) {
        if (value != null && !value.isBlank()) text.append(value).append('\n');
    }

    private List<String> nonNull(String... values) {
        return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank()).toList();
    }

    private int experienceRelevance(ExperienceEntry entry, Set<String> jobTokens) {
        int result = relevance(entry.jobTitle(), jobTokens) + relevance(entry.employer(), jobTokens);
        for (String highlight : entry.highlights()) result += relevance(highlight, jobTokens);
        return result;
    }

    private int relevance(String value, Set<String> jobTokens) {
        if (value == null) return 0;
        int result = 0;
        for (String token : tokens(value)) if (jobTokens.contains(token)) result++;
        return result;
    }

    private Set<String> tokens(String value) {
        if (value == null) return Set.of();
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+")) {
            if (token.length() >= 3) tokens.add(token);
        }
        return tokens;
    }
}
