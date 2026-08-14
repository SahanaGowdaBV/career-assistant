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
        assertEquals("Example Corp", parsed.structured().experience().getFirst().employer());
        assertEquals("Jan 2020 - Present", parsed.structured().experience().getFirst().employmentDates());
        assertTrue(parsed.structured().skills().containsAll(java.util.List.of("AWS", "Docker", "Kubernetes", "Terraform")));
        assertEquals(java.util.List.of("AWS Certified Example"), parsed.structured().certifications());
        assertEquals(java.util.List.of("Reduced verified deployment time by 20%."), parsed.structured().achievements());
    }

    private void paragraph(XWPFDocument document, String text) {
        document.createParagraph().createRun().setText(text);
    }
}
