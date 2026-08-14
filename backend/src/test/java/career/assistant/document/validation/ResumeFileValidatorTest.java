package career.assistant.document.validation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void rejectsPdfActiveContent() {
        ResumeFileValidator validator = new ResumeFileValidator(1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", ResumeFileValidator.PDF, "%PDF-1.7\n/JavaScript".getBytes()
        );

        assertThrows(ResumeValidationException.class, () -> validator.validate(file));
    }
}
