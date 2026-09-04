package career.assistant.job.service;

import career.assistant.company.entity.Company;
import career.assistant.company.service.CompanyService;
import career.assistant.job.dto.ManualJobRequest;
import career.assistant.job.entity.Job;
import career.assistant.job.exception.DuplicateJobException;
import career.assistant.job.repository.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualJobImportServiceTest {
    private final JobRepository jobs = mock(JobRepository.class);
    private final CompanyService companies = mock(CompanyService.class);
    private final ManualJobImportService service = new ManualJobImportService(jobs, companies);

    @BeforeEach
    void authenticate() {
        Jwt jwt = Jwt.withTokenValue("synthetic").header("alg", "none").subject("owner-a")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void savesOwnedManualJobWithoutFetchingTheUrl() {
        Company company = new Company();
        setId(company, UUID.randomUUID());
        when(companies.findOrCreate("Example Company")).thenReturn(company);
        when(jobs.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job saved = service.create(request("https://careers.example.com/jobs/123", "5-7 years"));

        assertEquals("owner-a", saved.getOwnerSubject());
        assertEquals("https://careers.example.com/jobs/123", saved.getCanonicalUrl());
        assertEquals("Bayt", saved.getSourcePortal());
        assertEquals(5, saved.getExperienceMin());
        assertEquals(7, saved.getExperienceMax());
        assertEquals("MANUAL", saved.getSource().name());
    }

    @Test
    void rejectsCanonicalDuplicateAcrossSources() {
        when(jobs.existsByCanonicalUrlIgnoreCase("https://careers.example.com/jobs/123")).thenReturn(true);
        assertThrows(DuplicateJobException.class,
                () -> service.create(request("https://CAREERS.example.com/jobs/123/?utm_source=portal", "")));
        verify(jobs, never()).save(any());
    }

    @Test
    void rejectsNonHttpsAndPrivateHostsWithoutNetworkAccess() {
        for (String url : new String[]{"http://careers.example.com/job", "https://localhost/job",
                "https://127.0.0.1/job", "https://careers.internal/job"}) {
            assertThrows(IllegalArgumentException.class, () -> service.create(request(url, "")), url);
        }
        verify(jobs, never()).save(any());
    }

    private ManualJobRequest request(String url, String experience) {
        return new ManualJobRequest("Platform Engineer", "Example Company", "Dubai, UAE", experience,
                "Build and operate cloud infrastructure using Kubernetes.", url, "Bayt");
    }

    private static void setId(Company company, UUID id) {
        try { var field = Company.class.getDeclaredField("id"); field.setAccessible(true); field.set(company, id); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
