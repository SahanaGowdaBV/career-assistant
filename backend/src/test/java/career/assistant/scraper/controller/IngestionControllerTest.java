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
}
