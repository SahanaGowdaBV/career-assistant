package career.assistant.document.customization;

import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ResumeContact;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ResumeCustomizer {

    private static final String FONT = "Arial";
    private static final String ACCENT = "17365D";

    public CustomizedResumeDocument customize(ParsedResume master, String jobText) {
        Set<String> jobTokens = tokens(jobText);
        List<String> skills = master.skills().stream()
                .sorted(Comparator.comparingInt((String value) -> relevance(value, jobTokens)).reversed())
                .toList();
        List<ExperienceEntry> experience = master.experience().stream()
                .map(entry -> new ExperienceEntry(
                        entry.employer(), entry.jobTitle(), entry.employmentDates(),
                        entry.highlights().stream()
                                .sorted(Comparator.comparingInt((String value) -> relevance(value, jobTokens)).reversed())
                                .toList()
                ))
                .toList();
        List<String> achievements = master.achievements().stream()
                .sorted(Comparator.comparingInt((String value) -> relevance(value, jobTokens)).reversed())
                .toList();

        ParsedResume customized = new ParsedResume(
                master.name(), master.contact(), master.professionalSummary(), experience, skills,
                master.certifications(), master.education(), achievements
        );
        String text = renderPlainText(customized);
        return new CustomizedResumeDocument(
                renderDocx(customized), text, customized,
                skills.stream().filter(skill -> relevance(skill, jobTokens) > 0).toList(), achievements
        );
    }

    private byte[] renderDocx(ParsedResume resume) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(document);
            BigInteger bulletNumbering = createBulletNumbering(document);
            addIdentity(document, resume);
            if (resume.professionalSummary() != null) {
                heading(document, "Professional Summary");
                body(document, resume.professionalSummary(), 3);
            }
            if (!resume.skills().isEmpty()) {
                heading(document, "Skills");
                body(document, String.join(", ", resume.skills()), 3);
            }
            if (!resume.experience().isEmpty()) {
                heading(document, "Professional Experience");
                for (int i = 0; i < resume.experience().size(); i++) {
                    ExperienceEntry entry = resume.experience().get(i);
                    XWPFParagraph header = paragraph(document, 1, 1.0);
                    header.setKeepNext(true);
                    keepLines(header);
                    XWPFRun title = run(header, entry.jobTitle(), 10.2, true, ACCENT);
                    run(header, " | ", 10.2, false, "333333");
                    run(header, entry.employer(), 10.2, true, "222222");
                    run(header, " | " + entry.employmentDates(), 9.5, false, "555555");
                    List<String> highlights = entry.highlights();
                    for (int bullet = 0; bullet < highlights.size(); bullet++) {
                        XWPFParagraph paragraph = paragraph(document, 1, 1.0);
                        paragraph.setNumID(bulletNumbering);
                        keepLines(paragraph);
                        paragraph.setKeepNext(bullet + 1 < highlights.size());
                        run(paragraph, highlights.get(bullet), 9.5, false, "222222");
                    }
                }
            }
            section(document, bulletNumbering, "Certifications", resume.certifications());
            section(document, bulletNumbering, "Education", resume.education());
            section(document, bulletNumbering, "Achievements", resume.achievements());
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Customized resume document could not be generated", exception);
        }
    }

    private void configurePage(XWPFDocument document) {
        var section = document.getDocument().getBody().addNewSectPr();
        var size = section.addNewPgSz();
        size.setW(BigInteger.valueOf(12_240));
        size.setH(BigInteger.valueOf(15_840));
        var margins = section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(792));
        margins.setBottom(BigInteger.valueOf(792));
        margins.setLeft(BigInteger.valueOf(936));
        margins.setRight(BigInteger.valueOf(936));
        margins.setHeader(BigInteger.valueOf(360));
        margins.setFooter(BigInteger.valueOf(360));
    }

    private void addIdentity(XWPFDocument document, ParsedResume resume) {
        XWPFParagraph name = paragraph(document, 0, 1.0);
        name.setAlignment(ParagraphAlignment.CENTER);
        run(name, resume.name(), 18, true, ACCENT);
        List<String> contact = new ArrayList<>();
        ResumeContact value = resume.contact();
        add(contact, value.email()); add(contact, value.phone()); add(contact, value.linkedin()); add(contact, value.location());
        XWPFParagraph details = paragraph(document, 4, 1.0);
        details.setAlignment(ParagraphAlignment.CENTER);
        run(details, String.join(" | ", contact), 9.2, false, "444444");
    }

    private void section(XWPFDocument document, BigInteger numbering, String title, List<String> values) {
        if (values.isEmpty()) return;
        heading(document, title);
        for (int i = 0; i < values.size(); i++) {
            XWPFParagraph paragraph = paragraph(document, 1, 1.0);
            paragraph.setNumID(numbering);
            keepLines(paragraph);
            paragraph.setKeepNext(i + 1 < values.size());
            run(paragraph, values.get(i), 9.5, false, "222222");
        }
    }

    private void heading(XWPFDocument document, String value) {
        XWPFParagraph paragraph = paragraph(document, 2, 1.0);
        paragraph.setKeepNext(true);
        XWPFRun run = run(paragraph, value.toUpperCase(Locale.ROOT), 11.2, true, ACCENT);
        run.setCharacterSpacing(15);
        var border = paragraph.getCTP().getPPr().addNewPBdr().addNewBottom();
        border.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(5));
        border.setColor("7F8FA6");
        border.setSpace(BigInteger.ONE);
    }

    private void body(XWPFDocument document, String value, int after) {
        XWPFParagraph paragraph = paragraph(document, after, 1.05);
        keepLines(paragraph);
        run(paragraph, value, 9.5, false, "222222");
    }

    private XWPFParagraph paragraph(XWPFDocument document, int afterPoints, double lineSpacing) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(afterPoints * 20);
        paragraph.setSpacingBetween(lineSpacing);
        return paragraph;
    }

    private XWPFRun run(XWPFParagraph paragraph, String value, double size, boolean bold, String color) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(FONT);
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(color);
        run.setText(value == null ? "" : value);
        return run;
    }

    private BigInteger createBulletNumbering(XWPFDocument document) {
        XWPFNumbering numbering = document.createNumbering();
        CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance();
        abstractNum.setAbstractNumId(BigInteger.ZERO);
        CTLvl level = abstractNum.addNewLvl();
        level.setIlvl(BigInteger.ZERO);
        level.addNewStart().setVal(BigInteger.ONE);
        level.addNewNumFmt().setVal(STNumberFormat.BULLET);
        level.addNewLvlText().setVal("•");
        var paragraph = level.addNewPPr();
        CTInd indent = paragraph.addNewInd();
        indent.setLeft(BigInteger.valueOf(360));
        indent.setHanging(BigInteger.valueOf(180));
        CTRPr properties = level.addNewRPr();
        CTFonts fonts = properties.addNewRFonts();
        fonts.setAscii(FONT);
        fonts.setHAnsi(FONT);
        BigInteger abstractId = numbering.addAbstractNum(new XWPFAbstractNum(abstractNum));
        return numbering.addNum(abstractId);
    }

    private int balancedSplitIndex(ParsedResume resume) {
        int fixed = 8 + estimateLines(resume.professionalSummary()) + estimateLines(String.join(", ", resume.skills()));
        int tail = resume.certifications().size() + resume.education().size() + resume.achievements().size() + 4;
        List<Integer> weights = resume.experience().stream().map(this::entryLines).toList();
        int total = fixed + tail + weights.stream().mapToInt(Integer::intValue).sum();
        if (total < 52 || weights.size() < 2) return -1;
        int target = total / 2;
        int current = fixed;
        int best = 1;
        int distance = Integer.MAX_VALUE;
        for (int i = 1; i < weights.size(); i++) {
            current += weights.get(i - 1);
            int nextDistance = Math.abs(target - current);
            if (nextDistance < distance) { distance = nextDistance; best = i; }
        }
        return best;
    }

    private void keepLines(XWPFParagraph paragraph) {
        var properties = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        if (!properties.isSetKeepLines()) properties.addNewKeepLines();
    }

    private int entryLines(ExperienceEntry entry) {
        return 1 + entry.highlights().stream().mapToInt(this::estimateLines).sum();
    }
    private int estimateLines(String value) { return value == null || value.isBlank() ? 0 : Math.max(1, (value.length() + 94) / 95); }

    private String renderPlainText(ParsedResume resume) {
        StringBuilder text = new StringBuilder();
        append(text, resume.name());
        ResumeContact contact = resume.contact();
        append(text, String.join(" | ", nonNull(contact.email(), contact.phone(), contact.linkedin(), contact.location())));
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

    private void appendSection(StringBuilder text, String title, List<String> values) {
        if (!values.isEmpty()) { text.append(title).append('\n'); values.forEach(value -> append(text, value)); }
    }
    private void append(StringBuilder text, String value) { if (value != null && !value.isBlank()) text.append(value).append('\n'); }
    private void add(List<String> values, String value) { if (value != null && !value.isBlank()) values.add(value); }
    private List<String> nonNull(String... values) { return java.util.Arrays.stream(values).filter(value -> value != null && !value.isBlank()).toList(); }
    private int relevance(String value, Set<String> jobTokens) {
        if (value == null) return 0;
        int result = 0;
        for (String token : tokens(value)) if (jobTokens.contains(token)) result++;
        return result;
    }
    private Set<String> tokens(String value) {
        if (value == null) return Set.of();
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+")) if (token.length() >= 3) tokens.add(token);
        return tokens;
    }
}
