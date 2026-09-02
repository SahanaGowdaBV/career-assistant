package career.assistant.document.parsing;

import career.assistant.document.model.ParsedResumeDocument;
import career.assistant.document.validation.ResumeFileValidator;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeParserTest {

    @Test
    void extractsAndStructuresDocxWithoutInventingFields() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            paragraph(document, "Jane Example");
            paragraph(document, "jane@example.com | +971 50 123 4567 | linkedin.com/in/jane-example");
            paragraph(document, "Location: Dubai, UAE");
            paragraph(document, "Professional Summary");
            paragraph(document, "DevOps engineer focused on reliable AWS platforms.");
            paragraph(document, "Professional Experience");
            paragraph(document, "Senior DevOps Engineer | Example Corp | Jan 2020 - Present");
            paragraph(document, "Built Docker and Kubernetes delivery platforms.");
            paragraph(document, "Skills");
            paragraph(document, "AWS, Docker, Kubernetes, Terraform");
            paragraph(document, "Certifications");
            paragraph(document, "AWS Certified Example");
            paragraph(document, "Education");
            paragraph(document, "Example University");
            paragraph(document, "Achievements");
            paragraph(document, "Reduced verified deployment time by 20%.");
            document.write(output);
            docx = output.toByteArray();
        }

        ParsedResumeDocument parsed = new ResumeParser().parse(docx, ResumeFileValidator.DOCX);

        assertTrue(parsed.originalText().contains("Example Corp"));
        assertEquals("Jane Example", parsed.structured().name());
        assertEquals("jane@example.com", parsed.structured().contact().email());
        assertEquals("+971 50 123 4567", parsed.structured().contact().phone());
        assertEquals("linkedin.com/in/jane-example", parsed.structured().contact().linkedin());
        assertEquals("Dubai, UAE", parsed.structured().contact().location());
        assertEquals("Example Corp", parsed.structured().experience().getFirst().employer());
        assertEquals("Jan 2020 - Present", parsed.structured().experience().getFirst().employmentDates());
        assertTrue(parsed.structured().skills().containsAll(java.util.List.of("AWS", "Docker", "Kubernetes", "Terraform")));
        assertEquals(java.util.List.of("AWS Certified Example"), parsed.structured().certifications());
        assertEquals(java.util.List.of("Reduced verified deployment time by 20%."), parsed.structured().achievements());
    }

    @Test
    void preservesMultiLineEmploymentEntriesAndRepairsWrappedBulletsWithoutMixingJobs() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String line : new String[]{
                    "Jane Example", "jane@example.com | +971 50 123 4567", "Professional Experience",
                    "Senior Platform Engineer", "Current Example", "Jan 2022 - Present",
                    "• Built reliable delivery", "platforms with AWS and Terraform.",
                    "Platform Engineer", "Earlier Example", "Jan 2020 - Dec 2021",
                    "• Maintained production Kubernetes and Docker systems.",
                    "Skills", "Cloud: AWS, Terraform", "AWS; Docker; Kubernetes"
            }) paragraph(document, line);
            document.write(output);
            docx = output.toByteArray();
        }

        ParsedResumeDocument parsed = new ResumeParser().parse(docx, ResumeFileValidator.DOCX);

        assertEquals(2, parsed.structured().experience().size());
        assertEquals("Current Example", parsed.structured().experience().get(0).employer());
        assertEquals("Senior Platform Engineer", parsed.structured().experience().get(0).jobTitle());
        assertEquals("Built reliable delivery platforms with AWS and Terraform.", parsed.structured().experience().get(0).highlights().getFirst());
        assertEquals("Earlier Example", parsed.structured().experience().get(1).employer());
        assertEquals("Platform Engineer", parsed.structured().experience().get(1).jobTitle());
        assertEquals(parsed.structured().skills().size(), parsed.structured().skills().stream().map(String::toLowerCase).distinct().count());
        assertTrue(parsed.structured().skills().stream().noneMatch(skill -> skill.contains(":")));
    }

    @Test
    void deterministicallyExtractsIconPrefixedPipeSeparatedContactRow() {
        var parsed = new ResumeParser().structure("""
                Jane Example
                ✉ candidate@example.com | ☎ +971 50 123 4567 | in linkedin.com/in/jane-example | ◉ github.com/jane-example | 📍 Dubai, UAE
                Professional Experience
                Platform Engineer | Example Corp | Jan 2022 - Present
                Built reliable platforms.
                """);

        assertEquals("candidate@example.com", parsed.contact().email());
        assertEquals("+971 50 123 4567", parsed.contact().phone());
        assertEquals("linkedin.com/in/jane-example", parsed.contact().linkedin());
        assertEquals("github.com/jane-example", parsed.contact().github());
        assertEquals("Dubai, UAE", parsed.contact().location());
    }

    @Test
    void flattensParenthesizedSkillsIntoIndependentAtsValues() {
        var parsed = new ResumeParser().structure("""
                Jane Example
                candidate@example.com | +971 50 123 4567
                Skills
                AWS (EC2, AWS CodeBuild, GitHub, Docker, Ansible, Helm, Jenkins, Linux)
                """);

        assertTrue(parsed.skills().containsAll(java.util.List.of("AWS", "Docker", "Ansible", "Helm", "Jenkins", "Linux")));
        assertTrue(parsed.skills().stream().noneMatch(skill -> skill.contains("(") || skill.contains(")")));
    }

    @Test
    void deduplicatesSkillAliases() {
        var parsed = new ResumeParser().structure("""
                Jane Example
                candidate@example.com | +971 50 123 4567
                Skills
                GitHub Actions, GitHub, Amazon CloudWatch, CloudWatch, AWS EC2
                """);

        assertEquals(1, parsed.skills().stream().filter("GitHub Actions"::equals).count());
        assertEquals(0, parsed.skills().stream().filter("GitHub"::equals).count());
        assertEquals(1, parsed.skills().stream().filter("Amazon CloudWatch"::equals).count());
        assertEquals(0, parsed.skills().stream().filter("CloudWatch"::equals).count());
    }

    private void paragraph(XWPFDocument document, String text) {
        document.createParagraph().createRun().setText(text);
    }
}
