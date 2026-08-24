package career.assistant.scraper.controller;

import career.assistant.company.entity.Company;
import career.assistant.company.service.CompanyService;
import career.assistant.job.entity.Job;
import career.assistant.job.service.JobService;
import career.assistant.scraper.config.JobSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IngestionControllerTest {

    @Test
    void unknownExperienceIsSavedForPendingReviewWithNullableRange() {
        JobService jobs = mock(JobService.class);
        CompanyService companies = mock(CompanyService.class);
        when(jobs.existsBySourceAndSourceJobId(JobSource.COMPANY_CAREER_PAGE, "g42-3198"))
                .thenReturn(false);
        when(companies.findOrCreate("G42")).thenReturn(new Company());
        when(jobs.create(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var controller = new IngestionController(jobs, companies);
        var request = new IngestionController.IngestRequest(false, List.of(
                new IngestionController.IngestJob(
                        "Platform Engineer",
                        "G42",
                        "Abu Dhabi, United Arab Emirates",
                        null,
                        null,
                        true,
                        JobSource.COMPANY_CAREER_PAGE,
                        "g42-3198",
                        "https://example.com/jobs/3198",
                        "Platform engineering role",
                        null
                )
        ));

        var result = controller.ingest(request);
        var captor = ArgumentCaptor.forClass(Job.class);
        org.mockito.Mockito.verify(jobs).create(captor.capture());
        Job saved = captor.getValue();

        assertEquals(1, result.accepted());
        assertEquals("PENDING_REVIEW", saved.getStatus());
        assertNull(saved.getExperienceMin());
        assertNull(saved.getExperienceMax());
    }

    @Test
    void canonicalJobUrlIsUsedForDuplicateDetection() {
        JobService jobs = mock(JobService.class);
        CompanyService companies = mock(CompanyService.class);
        when(jobs.existsBySourceAndSourceJobId(JobSource.COMPANY_CAREER_PAGE, "changed-id"))
                .thenReturn(false);
        when(jobs.existsByJobUrlIn(List.of("https://example.com/jobs/3198", "https://example.com/jobs/3198/")))
                .thenReturn(true);

        var controller = new IngestionController(jobs, companies);
        var result = controller.ingest(new IngestionController.IngestRequest(false, List.of(
                new IngestionController.IngestJob(
                        "Platform Engineer", "G42", "Abu Dhabi, UAE", 4, 8, false,
                        JobSource.COMPANY_CAREER_PAGE, "changed-id",
                        "HTTPS://EXAMPLE.COM:443/jobs/3198/#ignored", null, null
                )
        )));

        assertEquals(0, result.accepted());
        assertEquals(1, result.duplicates());
        verifyNoInteractions(companies);
    }

    @Test
    void repeatingLiveIngestionSavesOnceThenReportsDuplicate() {
        JobService jobs = mock(JobService.class);
        CompanyService companies = mock(CompanyService.class);
        Company company = new Company();
        when(jobs.existsBySourceAndSourceJobId(JobSource.COMPANY_CAREER_PAGE, "stable-id"))
                .thenReturn(false, true);
        when(jobs.existsByJobUrlIn(any())).thenReturn(false);
        when(companies.findOrCreate("G42")).thenReturn(company);
        when(jobs.create(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var controller = new IngestionController(jobs, companies);
        var request = new IngestionController.IngestRequest(false, List.of(
                new IngestionController.IngestJob(
                        "Platform Engineer", "G42", "Dubai, UAE", 4, 8, false,
                        JobSource.COMPANY_CAREER_PAGE, "stable-id",
                        "https://example.com/jobs/stable-id", null, null
                )
        ));

        var first = controller.ingest(request);
        var second = controller.ingest(request);

        assertEquals(1, first.accepted());
        assertEquals(1, first.jobs().size());
        assertEquals(0, first.duplicates());
        assertEquals(0, second.accepted());
        assertEquals(0, second.jobs().size());
        assertEquals(1, second.duplicates());
        verify(jobs, times(1)).create(any(Job.class));
    }
}
