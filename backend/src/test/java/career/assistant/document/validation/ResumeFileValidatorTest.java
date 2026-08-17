package career.assistant.document.validation;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResumeFileValidatorTest {

    @Test
    void rejectsInvalidFileSignatureAndMimeType() {
        ResumeFileValidator validator = new ResumeFileValidator(1024);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "not a PDF".getBytes());

        assertThrows(ResumeValidationException.class, () -> validator.validate(file));
    }

    @Test
    void rejectsOversizedFileBeforeParsing() {
        ResumeFileValidator validator = new ResumeFileValidator(8);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "%PDF-1234".getBytes());

        assertThrows(ResumeValidationException.class, () -> validator.validate(file));
    }

    @Test
    void sanitizesFilenameAndRemovesTraversal() {
        ResumeFileValidator validator = new ResumeFileValidator(1024);

        String sanitized = validator.sanitizeFilename("../../evil résumé?.PDF", "pdf");

        assertEquals("evil r_sum_.pdf", sanitized);
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains(".."));
    }

    @Test
    void acceptsOrdinaryResumePdf() throws IOException {
        ValidatedResumeFile result = validator().validate(pdfFile(ordinaryResumePdf()));

        assertEquals("resume.pdf", result.sanitizedFilename());
        assertNotNull(result.checksum());
    }

    @Test
    void acceptsPdfContainingStandardSafeResumeHyperlinks() throws IOException {
        byte[] pdf = createPdf(document -> {
            PDPage page = document.getPage(0);
            addUriLink(page, "https://example.com/candidate", 650);
            // Matches the actual resume's safe Link -> URI structure and HTTP scheme.
            addUriLink(page, "http://example.com/portfolio", 620);
            addUriLink(page, "mailto:candidate@example.com", 590);
        });

        assertNotNull(validator().validate(pdfFile(pdf)));
    }

    @Test
    void rejectsJavaScriptPdf() throws IOException {
        byte[] pdf = createPdf(document ->
                document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('unsafe')")));

        assertThrows(ResumeValidationException.class, () -> validator().validate(pdfFile(pdf)));
    }

    @Test
    void rejectsLaunchActionPdf() throws IOException {
        byte[] pdf = createPdf(document -> {
            PDActionLaunch launch = new PDActionLaunch();
            launch.setF("resume-helper.exe");
            document.getDocumentCatalog().setOpenAction(launch);
        });

        assertThrows(ResumeValidationException.class, () -> validator().validate(pdfFile(pdf)));
    }

    @Test
    void rejectsEmbeddedExecutablePdf() throws IOException {
        byte[] pdf = createPdf(document -> {
            PDComplexFileSpecification specification = new PDComplexFileSpecification();
            specification.setFile("resume-helper.exe");
            specification.setEmbeddedFile(new PDEmbeddedFile(
                    document, new ByteArrayInputStream(new byte[]{'M', 'Z', 0, 0})
            ));

            PDEmbeddedFilesNameTreeNode embeddedFiles = new PDEmbeddedFilesNameTreeNode();
            embeddedFiles.setNames(Map.of("resume-helper.exe", specification));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(document.getDocumentCatalog());
            names.setEmbeddedFiles(embeddedFiles);
            document.getDocumentCatalog().setNames(names);
        });

        assertThrows(ResumeValidationException.class, () -> validator().validate(pdfFile(pdf)));
    }

    @Test
    void rejectsExecutableUriScheme() throws IOException {
        byte[] pdf = createPdf(document -> addUriLink(
                document.getPage(0), "javascript:alert('unsafe')", 650
        ));

        assertThrows(ResumeValidationException.class, () -> validator().validate(pdfFile(pdf)));
    }

    private ResumeFileValidator validator() {
        return new ResumeFileValidator(1024 * 1024);
    }

    private MockMultipartFile pdfFile(byte[] content) {
        return new MockMultipartFile("file", "resume.pdf", ResumeFileValidator.PDF, content);
    }

    private void addUriLink(PDPage page, String uri, float y) throws IOException {
        PDAnnotationLink link = new PDAnnotationLink();
        link.setRectangle(new PDRectangle(72, y, 220, 20));
        PDActionURI action = new PDActionURI();
        action.setURI(uri);
        link.setAction(action);
        page.getAnnotations().add(link);
    }

    private byte[] ordinaryResumePdf() throws IOException {
        return createPdf(document -> {
            document.getDocumentInformation().setTitle("Resume metadata and tags are safe");
            PDPage page = document.getPage(0);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                // The slash proves that a harmless raw /JavaScript substring is no longer rejected.
                stream.showText("Resume skills: /JavaScript, Java, Spring, SQL");
                stream.endText();
            }
        });
    }

    private byte[] createPdf(PdfCustomizer customizer) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            customizer.customize(document);
            document.save(output);
            return output.toByteArray();
        }
    }

    @FunctionalInterface
    private interface PdfCustomizer {
        void customize(PDDocument document) throws IOException;
    }
}
