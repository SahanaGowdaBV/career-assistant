package career.assistant.document.service;

import career.assistant.document.customization.ResumeCustomizer;
import career.assistant.document.customization.TruthfulnessValidator;
import career.assistant.document.dto.ResumeDetailsResponse;
import career.assistant.document.entity.ResumeVersion;
import career.assistant.document.parsing.ResumeParser;
import career.assistant.document.quality.DocumentQualityGate;
import career.assistant.document.repository.ResumeVersionRepository;
import career.assistant.document.storage.InMemoryResumeStorage;
import career.assistant.document.storage.ResumeStorage;
import career.assistant.document.storage.ResumeStorageException;
import career.assistant.document.validation.ResumeFileValidator;
import career.assistant.job.repository.JobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeServiceTest {

    @Test
    void uploadsAndParsesPdfWithChecksumAndNextVersion() throws Exception {
        ResumeVersionRepository repository = mock(ResumeVersionRepository.class);
        ResumeVersion previous = new ResumeVersion();
        previous.setVersionNumber(4);
        when(repository.findTopByOrderByVersionNumberDesc()).thenReturn(Optional.of(previous));
        when(repository.save(any(ResumeVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ResumeService service = service(repository, new InMemoryResumeStorage());
        MockMultipartFile upload = new MockMultipartFile("file", "../verified master.pdf", ResumeFileValidator.PDF, pdfResume());

        ResumeDetailsResponse response = service.upload(upload, true);

        ArgumentCaptor<ResumeVersion> saved = ArgumentCaptor.forClass(ResumeVersion.class);
        verify(repository).save(saved.capture());
        assertEquals(5, response.version());
        assertEquals("verified master.pdf", response.filename());
        assertTrue(response.master());
        assertEquals(64, response.checksum().length());
        assertTrue(response.parsed().skills().contains("AWS"));
        assertTrue(saved.getValue().getStructuredExperience().contains("jane@example.com"));
        assertNotNull(saved.getValue().getStoragePath());
        assertTrue(saved.getValue().getStoragePath().matches("\\d{4}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.pdf"));
        verify(repository).deactivateAllMasters();
    }

    @Test
    void activatesMasterAtomicallyAndProtectsItFromDeletion() {
        ResumeVersionRepository repository = mock(ResumeVersionRepository.class);
        ResumeVersion version = new ResumeVersion();
        version.setVersionNumber(2);
        version.setVersionName("Resume v2");
        version.setStructuredSkills("[]");
        version.setStructuredExperience("{\"name\":null,\"professionalSummary\":null,\"experience\":[],\"skills\":[],\"certifications\":[],\"education\":[],\"achievements\":[]}");
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(version));
        when(repository.save(version)).thenReturn(version);
        ResumeService service = service(repository, new InMemoryResumeStorage());

        assertTrue(service.activateMaster(id).master());
        verify(repository).deactivateAllMasters();
        assertThrows(ResumeConflictException.class, () -> service.delete(id));
        verify(repository, never()).delete(version);
    }

    @Test
    void doesNotCreateDatabaseRowWhenStorageFails() throws Exception {
        ResumeVersionRepository repository = mock(ResumeVersionRepository.class);
        when(repository.findTopByOrderByVersionNumberDesc()).thenReturn(Optional.empty());
        ResumeStorage failingStorage = mock(ResumeStorage.class);
        org.mockito.Mockito.doThrow(new ResumeStorageException("Unable to store resume file"))
                .when(failingStorage).store(any(), any(), any());
        ResumeService service = service(repository, failingStorage);
        MockMultipartFile upload = new MockMultipartFile("file", "resume.pdf", ResumeFileValidator.PDF, pdfResume());

        assertThrows(ResumeStorageException.class, () -> service.upload(upload, false));
        verify(repository, never()).save(any());
    }

    private ResumeService service(ResumeVersionRepository repository, ResumeStorage storage) {
        ResumeJsonCodec codec = new ResumeJsonCodec(new ObjectMapper());
        return new ResumeService(
                repository,
                mock(JobRepository.class),
                storage,
                new ResumeFileValidator(5 * 1024 * 1024),
                new ResumeParser(),
                codec,
                new ResumeCustomizer(),
                new TruthfulnessValidator(),
                new DocumentQualityGate()
        );
    }

    private byte[] pdfResume() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.setLeading(14);
                content.newLineAtOffset(60, 740);
                for (String line : new String[]{"Jane Example", "jane@example.com | +971 50 123 4567 | linkedin.com/in/jane-example | github.com/jane-example | Dubai, UAE", "Professional Summary", "DevOps engineer with AWS delivery experience.", "Skills", "AWS, Docker", "Experience", "DevOps Engineer | Example Corp | Jan 2020 - Present", "Built reliable AWS systems."}) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
