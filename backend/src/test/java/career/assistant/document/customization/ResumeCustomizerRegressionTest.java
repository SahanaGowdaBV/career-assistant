package career.assistant.document.customization;

import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ResumeContact;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeCustomizerRegressionTest {
    @Test
    void generatedDocxKeepsChronologyContactsRealBulletsAndWholeEntries() throws Exception {
        ParsedResume master = new ParsedResume(
                "Jane Example", new ResumeContact("jane@example.com", "+971 50 123 4567", "linkedin.com/in/jane", "Dubai, UAE"),
                "Platform engineer building reliable cloud delivery systems.",
                List.of(
                        new ExperienceEntry("Current Example", "Senior Platform Engineer", "Jan 2022 - Present", List.of("Built reliable AWS delivery platforms.", "Improved Terraform deployment automation.")),
                        new ExperienceEntry("Earlier Example", "Platform Engineer", "Jan 2020 - Dec 2021", List.of("Maintained production Kubernetes systems.", "Supported Docker delivery workflows."))
                ),
                List.of("AWS", "Terraform", "Kubernetes", "Docker"), List.of("AWS Certification"), List.of("Example University"), List.of()
        );

        CustomizedResumeDocument output = new ResumeCustomizer().customize(master, "AWS Terraform platform role");
        assertEquals("Current Example", output.structured().experience().get(0).employer());
        assertEquals("Earlier Example", output.structured().experience().get(1).employer());
        Files.createDirectories(Path.of("target/generated-documents"));
        Files.write(Path.of("target/generated-documents/representative-resume.docx"), output.content());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.content()))) {
            String text = document.getParagraphs().stream().map(paragraph -> paragraph.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(text.contains("Jane Example"));
            assertTrue(text.contains("jane@example.com"));
            assertTrue(text.indexOf("Current Example") < text.indexOf("Earlier Example"));
            assertTrue(document.getParagraphs().stream().anyMatch(paragraph -> paragraph.getNumID() != null));
            assertFalse(document.getParagraphs().stream().anyMatch(paragraph -> paragraph.getText().startsWith("•")));
            document.getParagraphs().stream().filter(paragraph -> paragraph.getText().contains("Example | ")).forEach(paragraph -> assertNotNull(paragraph.getCTP().getPPr().getKeepNext()));
        }
    }
}
