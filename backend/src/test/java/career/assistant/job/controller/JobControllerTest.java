package career.assistant.job.controller;

import career.assistant.api.GlobalApiExceptionHandler;
import career.assistant.api.ResourceNotFoundException;
import career.assistant.job.entity.Job;
import career.assistant.job.exception.DuplicateJobException;
import career.assistant.job.service.JobService;
import career.assistant.job.service.ManualJobImportService;
import career.assistant.company.repository.CompanyRepository;
import career.assistant.company.entity.Company;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobControllerTest {

    private JobService jobService;
    private CompanyRepository companies;
    private MockMvc mockMvc;
    private ManualJobImportService manualJobs;

    @BeforeEach
    void setUp() {
        jobService = mock(JobService.class);
        companies = mock(CompanyRepository.class);
        manualJobs = mock(ManualJobImportService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new JobController(jobService, companies, manualJobs))
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    void returnsJobsAsResponseDtos() throws Exception {
        Job job = job();
        when(jobService.findAll()).thenReturn(List.of(job));
        Company company = new Company(); company.setName("Ziina");
        when(companies.findById(job.getCompanyId())).thenReturn(java.util.Optional.of(company));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Platform Engineer"))
                .andExpect(jsonPath("$[0].companyName").value("Ziina"))
                .andExpect(jsonPath("$[0].source").value("LINKEDIN"));
    }

    @Test
    void createsValidatedJob() throws Exception {
        when(jobService.create(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Platform Engineer"))
                .andExpect(jsonPath("$.sourceJobId").value("linkedin-123"));
    }

    @Test
    void returnsStructuredValidationErrors() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.title").exists())
                .andExpect(jsonPath("$.validationErrors.companyId").exists());
    }

    @Test
    void returnsConflictForDuplicateJob() throws Exception {
        when(jobService.create(any(Job.class)))
                .thenThrow(new DuplicateJobException("Job already exists"));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Job already exists"));
    }

    @Test
    void returnsConflictForDatabaseConstraintFailure() throws Exception {
        when(jobService.create(any(Job.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("The request conflicts with a database constraint"));
    }

    @Test
    void returnsNotFoundForMissingJob() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.findRequired(id))
                .thenThrow(new ResourceNotFoundException("Job " + id + " was not found"));

        mockMvc.perform(get("/api/jobs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returnsBadRequestForMalformedId() throws Exception {
        mockMvc.perform(get("/api/jobs/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'id'"));
    }

    @Test
    void createsValidatedManualJob() throws Exception {
        Job manual = job();
        manual.setSource(career.assistant.scraper.config.JobSource.MANUAL);
        manual.setSourcePortal("Bayt");
        when(manualJobs.create(any())).thenReturn(manual);

        mockMvc.perform(post("/api/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Platform Engineer","company":"Example","location":"Dubai, UAE",
                                 "experienceText":"5+ years","description":"A complete public job description.",
                                 "applicationUrl":"https://careers.example.com/jobs/123","sourcePortal":"Bayt"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.sourcePortal").value("Bayt"));
    }

    @Test
    void rejectsIncompleteManualJobBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/jobs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").exists())
                .andExpect(jsonPath("$.validationErrors.description").exists())
                .andExpect(jsonPath("$.validationErrors.applicationUrl").exists());
    }

    private Job job() {
        Job job = new Job();
        job.setTitle("Platform Engineer");
        job.setCompanyId(UUID.randomUUID());
        job.setSource(career.assistant.scraper.config.JobSource.LINKEDIN);
        job.setSourceJobId("linkedin-123");
        job.setJobUrl("https://example.test/jobs/123");
        return job;
    }

    private String validJson() {
        return """
                {
                  "title": "Platform Engineer",
                  "companyId": "%s",
                  "source": "LINKEDIN",
                  "sourceJobId": "linkedin-123",
                  "jobUrl": "https://example.test/jobs/123"
                }
                """.formatted(UUID.randomUUID());
    }
}
