package career.assistant.application.service;

import career.assistant.application.ats.AshbyPublicFormDiscovery;
import career.assistant.application.ats.AshbyResponseClassifier;
import career.assistant.application.ats.AtsAdapter;
import career.assistant.application.ats.PublicAtsAdapters;
import career.assistant.application.entity.Application;
import career.assistant.application.entity.ApplicationStatus;
import career.assistant.application.repository.ApplicationRepository;
import career.assistant.company.repository.CompanyRepository;
import career.assistant.document.dto.ResumeDetailsResponse;
import career.assistant.document.entity.CoverLetter;
import career.assistant.document.entity.ResumeVersion;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ResumeContact;
import career.assistant.document.service.CoverLetterService;
import career.assistant.document.service.ResumeConflictException;
import career.assistant.document.service.ResumeService;
import career.assistant.job.entity.Job;
import career.assistant.job.service.JobService;
import career.assistant.jobscore.entity.JobScore;
import career.assistant.jobscore.service.JobScoringService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplicationWorkflowServiceTest {
    ApplicationRepository apps = mock(ApplicationRepository.class);
    JobService jobs = mock(JobService.class);
    JobScoringService scoring = mock(JobScoringService.class);
    ResumeService resumes = mock(ResumeService.class);
    CoverLetterService letters = mock(CoverLetterService.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    ApplicationNotificationService mail = mock(ApplicationNotificationService.class);
    AshbyPublicFormDiscovery discovery = mock(AshbyPublicFormDiscovery.class);
    PublicAtsAdapters adapters = new PublicAtsAdapters(discovery, new AshbyResponseClassifier(new ObjectMapper()));
    ApplicationWorkflowService service = service();

    @Test void lowConfidenceRequiresConfirmationAndCreatesNoDocuments() {
        UUID id = UUID.randomUUID(); Job job = mock(Job.class); JobScore score = score("LOW", 95);
        when(jobs.findRequired(id)).thenReturn(job); when(scoring.findOrScore(job)).thenReturn(score);
        assertThrows(ResumeConflictException.class, () -> service.generate(id, false));
        verifyNoInteractions(resumes, letters);
    }

    @Test void duplicatePackageIsReturnedWithoutRegeneration() {
        UUID jobId = UUID.randomUUID(); Job job = job(jobId); Application application = packaged(job, ApplicationStatus.PENDING_REVIEW);
        when(jobs.findRequired(jobId)).thenReturn(job); when(scoring.findOrScore(job)).thenReturn(score("HIGH", 95));
        when(apps.findByJobId(jobId)).thenReturn(Optional.of(application));
        service.generate(jobId, false);
        verifyNoInteractions(resumes, letters);
    }

    @Test void scoreBelowConfiguredMinimumRoutesReadyApplicationToReview() {
        Job job = job(UUID.randomUUID()); Application application = packaged(job, ApplicationStatus.READY_TO_APPLY);
        arrangeRun(application, score("HIGH", 79));
        var result = service.run(UUID.randomUUID());
        assertEquals(ApplicationStatus.PENDING_REVIEW, result.status());
        assertTrue(result.reason().contains("score below 80"));
    }

    @Test void eligibleDryRunDoesNotSendSuccessEmailOrCreateConfirmation() {
        Job job = job(UUID.randomUUID()); Application application = packaged(job, ApplicationStatus.READY_TO_APPLY);
        arrangeRun(application, score("HIGH", 80));
        var result = service.run(UUID.randomUUID());
        assertEquals(ApplicationStatus.READY_TO_APPLY, result.status());
        assertNull(result.confirmationId());
        assertTrue(result.reason().contains("no request was submitted"));
        verifyNoInteractions(mail);
    }

    @Test void statusAndPriorAttemptAreHardEligibilityGates() {
        Job job = job(UUID.randomUUID()); Application pending = packaged(job, ApplicationStatus.PENDING_REVIEW);
        arrangeRun(pending, score("HIGH", 95));
        assertTrue(service.run(UUID.randomUUID()).reason().contains("not READY_TO_APPLY"));
        pending.setStatus(ApplicationStatus.READY_TO_APPLY); pending.setSubmissionAttemptedAt(java.time.OffsetDateTime.now());
        assertTrue(service.run(UUID.randomUUID()).reason().contains("already submitted or attempted"));
    }

    @Test void ashbyUnexpectedMandatoryQuestionRoutesToReview() {
        Job job = job(UUID.randomUUID());
        when(job.getJobUrl()).thenReturn("https://jobs.ashbyhq.com/ziina/b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb");
        Application application = packaged(job, ApplicationStatus.READY_TO_APPLY);
        when(discovery.discover(job.getJobUrl())).thenReturn(new AtsAdapter.FormDefinition("b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb",
                List.of(new AtsAdapter.FormField("custom_visa", "Visa status", "ValueSelect", true, List.of("Yes", "No"))), true));
        arrangeRun(application, score("HIGH", 95));
        assertTrue(service.run(UUID.randomUUID()).reason().contains("Visa status"));
    }

    @Test void ziinaPreviewIsRedactedAndIneligibleAtFiftyFourPercent() {
        UUID jobId = UUID.randomUUID();
        String url = "https://jobs.ashbyhq.com/ziina/b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb";
        Job job = job(jobId);
        when(job.getJobUrl()).thenReturn(url);
        Application application = packaged(job, ApplicationStatus.PENDING_REVIEW);
        ResumeVersion master = new ResumeVersion();
        ParsedResume parsed = new ParsedResume("Jane Example", new ResumeContact("jane@example.com", "+971 50 123 4567", null, "Dubai, UAE"), null, List.of(), List.of("AWS"), List.of(), List.of(), List.of());
        when(resumes.activeMasterEntity()).thenReturn(Optional.of(master)); when(resumes.parsed(master)).thenReturn(parsed);
        UUID resumeId = application.getResumeVersionId();
        when(resumes.get(resumeId)).thenReturn(new ResumeDetailsResponse(resumeId, "resume.docx", null, 2, "CUSTOMIZED", false, true, CoverLetterService.DOCX, 1234, "checksum", "", parsed, null, jobId, ""));
        when(discovery.discover(url)).thenReturn(new AtsAdapter.FormDefinition("b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb", List.of(
                new AtsAdapter.FormField("_systemfield_name", "Name", "String", true, List.of()),
                new AtsAdapter.FormField("_systemfield_email", "Email", "Email", true, List.of()),
                new AtsAdapter.FormField("_systemfield_resume", "Resume", "File", true, List.of()),
                new AtsAdapter.FormField("_systemfield_phone", "Phone", "Phone", true, List.of())
        ), true));
        when(apps.findById(any())).thenReturn(Optional.of(application)); when(scoring.findOrScore(job)).thenReturn(score("LOW", 54));

        var preview = service.preview(UUID.randomUUID());

        assertEquals("ASHBY", preview.adapter()); assertFalse(preview.eligible()); assertTrue(preview.eligibilityReason().contains("score below 80"));
        assertTrue(preview.formTokenPresent()); assertTrue(preview.missingRequiredAnswers().isEmpty());
        assertFalse(preview.toString().contains("jane@example.com")); assertFalse(preview.toString().contains("+971"));
        assertTrue(preview.safetyNotice().contains("no request was submitted"));
    }

    @Test void regenerationReusesApplicationAndAssignsNewDocumentVersions() {
        Job job = job(UUID.randomUUID()); Application application = packaged(job, ApplicationStatus.PENDING_REVIEW);
        UUID newResume = UUID.randomUUID(), newLetter = UUID.randomUUID();
        ResumeDetailsResponse resume = new ResumeDetailsResponse(newResume, "new.docx", null, 3, "CUSTOMIZED", false, true, CoverLetterService.DOCX, 100, "sum", "", null, null, job.getId(), "");
        CoverLetter letter = new CoverLetter(); setId(letter, newLetter);
        when(apps.findById(any())).thenReturn(Optional.of(application)); when(resumes.createCustomizedVersion(job.getId())).thenReturn(resume);
        when(letters.generateNewVersion(job)).thenReturn(letter); when(scoring.findOrScore(job)).thenReturn(score("HIGH", 95)); when(apps.save(application)).thenReturn(application);

        var result = service.regenerate(UUID.randomUUID());

        assertEquals(newResume, result.resumeVersionId()); assertEquals(newLetter, result.coverLetterId());
        assertEquals(ApplicationStatus.PENDING_REVIEW, result.status()); verify(apps).save(application);
    }

    @Test void approveRejectAndManualAppliedRemainExplicitTransitions() {
        Job job = job(UUID.randomUUID()); Application application = packaged(job, ApplicationStatus.PENDING_REVIEW);
        when(apps.findById(any())).thenReturn(Optional.of(application)); when(scoring.findOrScore(job)).thenReturn(score("HIGH", 95)); when(apps.save(application)).thenReturn(application);
        assertEquals(ApplicationStatus.READY_TO_APPLY, service.review(UUID.randomUUID(), true, null).status());
        assertEquals(ApplicationStatus.REJECTED, service.review(UUID.randomUUID(), false, null).status());
        application.setStatus(ApplicationStatus.READY_TO_APPLY);
        assertThrows(ResumeConflictException.class, () -> service.markManuallyApplied(UUID.randomUUID(), false, null));
        var applied = service.markManuallyApplied(UUID.randomUUID(), true, "manual confirmation");
        assertEquals(ApplicationStatus.MANUALLY_APPLIED, applied.status()); assertNotNull(applied.appliedAt()); verify(mail).sendVerifiedSuccessOnce(application, "95");
    }

    private ApplicationWorkflowService service() {
        return new ApplicationWorkflowService(apps, jobs, scoring, resumes, letters, companies, adapters, mail,
                false, true, 1, BigDecimal.valueOf(80), "boards.greenhouse.io,jobs.lever.co,apply.workable.com,jobs.ashbyhq.com");
    }
    private void arrangeRun(Application application, JobScore score) {
        when(apps.findById(any())).thenReturn(Optional.of(application)); when(scoring.findOrScore(application.getJob())).thenReturn(score);
        when(resumes.activeMasterEntity()).thenReturn(Optional.of(new ResumeVersion())); when(apps.save(application)).thenReturn(application);
    }
    private static Application packaged(Job job, ApplicationStatus status) {
        Application application = new Application(); application.setJob(job); application.setStatus(status);
        application.setResumeVersionId(UUID.randomUUID()); application.setCoverLetterId(UUID.randomUUID()); return application;
    }
    private static Job job(UUID id) {
        Job job = mock(Job.class); when(job.getId()).thenReturn(id); when(job.getTitle()).thenReturn("DevOps Engineer");
        when(job.getCompanyId()).thenReturn(UUID.randomUUID()); when(job.getJobUrl()).thenReturn("https://boards.greenhouse.io/acme/jobs/12345");
        when(job.getLocation()).thenReturn("Dubai, UAE"); when(job.getCountry()).thenReturn("UAE"); return job;
    }
    private static JobScore score(String confidence, int value) {
        JobScore score = new JobScore(); score.setScore(BigDecimal.valueOf(value)); score.setScoringConfidence(confidence);
        score.setRequiredSkillsScore(BigDecimal.valueOf(100)); score.setMatchedKeywords("AWS, Terraform"); score.setMissingKeywords(""); return score;
    }
    private static void setId(CoverLetter letter, UUID id) {
        try { var field = CoverLetter.class.getDeclaredField("id"); field.setAccessible(true); field.set(letter, id); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
