package career.assistant.cleanup;

import career.assistant.application.entity.Application;
import career.assistant.application.entity.ApplicationStatus;
import career.assistant.application.repository.ApplicationRepository;
import career.assistant.document.entity.CoverLetter;
import career.assistant.document.entity.ResumeVersion;
import career.assistant.document.repository.CoverLetterRepository;
import career.assistant.document.repository.ResumeVersionRepository;
import career.assistant.document.storage.ResumeStorage;
import career.assistant.job.entity.Job;
import career.assistant.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetentionCleanupServiceTest {

    private ResumeVersionRepository resumes;
    private CoverLetterRepository letters;
    private ApplicationRepository applications;
    private JobRepository jobs;
    private CleanupAuditRepository audits;
    private ResumeStorage storage;

    @BeforeEach
    void setUp() {
        resumes = mock(ResumeVersionRepository.class);
        letters = mock(CoverLetterRepository.class);
        applications = mock(ApplicationRepository.class);
        jobs = mock(JobRepository.class);
        audits = mock(CleanupAuditRepository.class);
        storage = mock(ResumeStorage.class);
        when(resumes.findByCreatedAtBefore(any())).thenReturn(List.of());
        when(letters.findByCreatedAtBefore(any())).thenReturn(List.of());
        when(applications.findByUpdatedAtBefore(any())).thenReturn(List.of());
        when(jobs.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of());
    }

    @Test
    void previewAlwaysProtectsMasterAndUploadedResumes() {
        ResumeVersion master = generatedResume(true, "private/master.docx");
        ResumeVersion uploaded = mock(ResumeVersion.class);
        when(uploaded.getId()).thenReturn(UUID.randomUUID());
        when(uploaded.getStoragePath()).thenReturn("private/uploaded.docx");
        when(resumes.findByCreatedAtBefore(any())).thenReturn(List.of(master, uploaded));

        var result = service(false).preview();

        assertTrue(result.dryRun());
        assertEquals(0, result.candidates());
        verifyNoInteractions(storage);
        verifyNoInteractions(audits);
    }

    @Test
    void protectedApplicationReferencesKeepGeneratedDocuments() {
        ResumeVersion resume = generatedResume(false, "private/generated.docx");
        CoverLetter letter = generatedLetter("private/letter.docx");
        when(resumes.findByCreatedAtBefore(any())).thenReturn(List.of(resume));
        when(letters.findByCreatedAtBefore(any())).thenReturn(List.of(letter));
        when(applications.existsByResumeVersionIdAndStatusIn(any(), any())).thenReturn(true);
        when(applications.existsByCoverLetterIdAndStatusIn(any(), any())).thenReturn(true);

        var result = service(true).run();

        assertEquals(0, result.candidates());
        verify(resumes, never()).delete(any());
        verify(letters, never()).delete(any());
        verifyNoInteractions(storage);
    }

    @Test
    void previewCountsCandidatesAndAuditsWithoutDeleting() {
        Application failed = application(ApplicationStatus.FAILED, 91);
        Application successful = application(ApplicationStatus.AUTO_APPLIED, 366);
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        ResumeVersion resume = generatedResume(false, "private/generated.docx");
        CoverLetter letter = generatedLetter("private/letter.docx");
        when(applications.findByUpdatedAtBefore(any())).thenReturn(List.of(failed, successful));
        when(jobs.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(job));
        when(resumes.findByCreatedAtBefore(any())).thenReturn(List.of(resume));
        when(letters.findByCreatedAtBefore(any())).thenReturn(List.of(letter));

        var result = service(false).preview();

        assertTrue(result.dryRun());
        assertEquals(5, result.candidates());
        assertEquals(2, result.categories().get("APPLICATION"));
        assertEquals(1, result.categories().get("JOB"));
        assertEquals(1, result.categories().get("RESUME"));
        assertEquals(1, result.categories().get("COVER_LETTER"));
        assertEquals(0, result.deleted());
        verify(audits, times(5)).save(any());
        verify(applications, never()).delete(any());
        verify(jobs, never()).delete(any(Job.class));
        verify(resumes, never()).delete(any());
        verify(letters, never()).delete(any());
        verifyNoInteractions(storage);

        ArgumentCaptor<CleanupAuditEntry> auditCaptor = ArgumentCaptor.forClass(CleanupAuditEntry.class);
        verify(audits, times(5)).save(auditCaptor.capture());
        assertTrue(auditCaptor.getAllValues().stream().allMatch(this::isPreviewAudit));
    }

    @Test
    void realRunDeletesStorageAndRecordsBeforeDatabaseRecords() {
        ResumeVersion resume = generatedResume(false, "private/generated.docx");
        CoverLetter letter = generatedLetter("private/letter.docx");
        when(resumes.findByCreatedAtBefore(any())).thenReturn(List.of(resume));
        when(letters.findByCreatedAtBefore(any())).thenReturn(List.of(letter));

        var result = service(false).run();

        assertEquals(2, result.candidates());
        assertEquals(2, result.deleted());
        verify(storage).delete("private/generated.docx");
        verify(storage).delete("private/letter.docx");
        verify(resumes).delete(resume);
        verify(letters).delete(letter);
        verify(audits, times(2)).save(any());
    }

    @Test
    void referencedRejectedOrArchivedJobIsNeverDeleted() {
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        when(jobs.findByStatusInAndUpdatedAtBefore(any(), any())).thenReturn(List.of(job));
        when(applications.existsByJobId(job.getId())).thenReturn(true);

        var result = service(false).run();

        assertEquals(0, result.categories().get("JOB"));
        verify(jobs, never()).delete(any(Job.class));
    }

    @Test
    void failedAndSuccessfulApplicationsRespectTheirOwnCutoffs() {
        Application recentFailure = application(ApplicationStatus.FAILED, 89);
        Application oldFailure = application(ApplicationStatus.FAILED, 91);
        Application recentSuccess = application(ApplicationStatus.MANUALLY_APPLIED, 364);
        Application oldSuccess = application(ApplicationStatus.MANUALLY_APPLIED, 366);
        when(applications.findByUpdatedAtBefore(any())).thenReturn(
                List.of(recentFailure, oldFailure, recentSuccess, oldSuccess));

        var result = service(false).preview();

        assertEquals(2, result.categories().get("APPLICATION"));
        assertEquals(2, result.candidates());
    }

    private RetentionCleanupService service(boolean dryRun) {
        return new RetentionCleanupService(
                resumes, letters, applications, jobs, audits, storage,
                false, dryRun, 30, 90, 90, 365
        );
    }

    private ResumeVersion generatedResume(boolean master, String path) {
        ResumeVersion resume = mock(ResumeVersion.class);
        when(resume.getId()).thenReturn(UUID.randomUUID());
        when(resume.isCustomized()).thenReturn(true);
        when(resume.isMasterResume()).thenReturn(master);
        when(resume.getStoragePath()).thenReturn(path);
        return resume;
    }

    private CoverLetter generatedLetter(String path) {
        CoverLetter letter = mock(CoverLetter.class);
        when(letter.getId()).thenReturn(UUID.randomUUID());
        when(letter.isCustomized()).thenReturn(true);
        when(letter.getStoragePath()).thenReturn(path);
        return letter;
    }

    private Application application(ApplicationStatus status, long ageInDays) {
        Application application = mock(Application.class);
        when(application.getId()).thenReturn(UUID.randomUUID());
        when(application.getStatus()).thenReturn(status);
        when(application.getUpdatedAt()).thenReturn(OffsetDateTime.now().minusDays(ageInDays));
        return application;
    }

    private boolean isPreviewAudit(CleanupAuditEntry entry) {
        try {
            var dryRunField = CleanupAuditEntry.class.getDeclaredField("dryRun");
            var actionField = CleanupAuditEntry.class.getDeclaredField("action");
            dryRunField.setAccessible(true);
            actionField.setAccessible(true);
            return dryRunField.getBoolean(entry) && "WOULD_DELETE".equals(actionField.get(entry));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
