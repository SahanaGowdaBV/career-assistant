package career.assistant.jobscore.service;

import career.assistant.document.entity.ResumeVersion;
import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.repository.ResumeVersionRepository;
import career.assistant.document.service.ResumeJsonCodec;
import career.assistant.job.entity.Job;
import career.assistant.job.repository.JobRepository;
import career.assistant.jobscore.entity.JobScore;
import career.assistant.jobscore.repository.JobScoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobScoringServiceTest {

    private final JobScoreRepository repository = mock(JobScoreRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final JobScoringService service = new JobScoringService(repository);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void scoresIdenticalInputDeterministically() {
        when(repository.findByJob(any(Job.class))).thenReturn(Optional.empty());
        when(repository.save(any(JobScore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job firstInput = targetJob();
        Job secondInput = targetJob();

        JobScore first = service.scoreJob(firstInput);
        JobScore second = service.scoreJob(secondInput);

        assertEquals(new BigDecimal("100.00"), first.getScore());
        assertEquals(first.getScore(), second.getScore());
        assertEquals(first.getMatchedKeywords(), second.getMatchedKeywords());
        assertEquals(first.getMissingKeywords(), second.getMissingKeywords());
        assertEquals(first.getScoringReason(), second.getScoringReason());
    }

    @Test
    void appliesDocumentedWeightsForPartialMatch() {
        when(repository.findByJob(any(Job.class))).thenReturn(Optional.empty());
        when(repository.save(any(JobScore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job job = new Job();
        job.setTitle("AWS Engineer");
        job.setDescription("Docker and Linux");
        job.setExperienceMin(4);
        job.setExperienceMax(6);
        job.setCountry("United Arab Emirates");

        JobScore score = service.scoreJob(job);

        assertEquals(new BigDecimal("51.67"), score.getScore());
        assertEquals(new BigDecimal("33.33"), score.getSkillsScore());
        assertEquals(BigDecimal.valueOf(100), score.getExperienceScore());
        assertEquals(BigDecimal.valueOf(100), score.getLocationScore());
        assertEquals(BigDecimal.ZERO, score.getSalaryScore());
    }

    @Test
    void scoresAgainstActiveMasterResumeWithExplainableComponents() {
        ResumeVersionRepository resumes = mock(ResumeVersionRepository.class);
        ResumeJsonCodec codec = new ResumeJsonCodec(new ObjectMapper());
        ParsedResume parsed = new ParsedResume(
                "Jane Example",
                "DevOps engineer building reliable cloud platforms.",
                List.of(new ExperienceEntry("Example Corp", "DevOps Engineer", "Jan 2020 - Present", List.of("Built AWS Docker Kubernetes Terraform platforms."))),
                List.of("AWS", "Docker", "Kubernetes", "Terraform"),
                List.of(), List.of(), List.of()
        );
        ResumeVersion master = new ResumeVersion();
        master.setMasterResume(true);
        master.setVersionNumber(3);
        master.setParsedText("DevOps Engineer Example Corp Jan 2020 - Present AWS Docker Kubernetes Terraform cloud platforms");
        master.setStructuredExperience(codec.write(parsed));
        when(resumes.findFirstByMasterResumeTrue()).thenReturn(Optional.of(master));
        when(repository.findByJob(any(Job.class))).thenReturn(Optional.empty());
        when(repository.save(any(JobScore.class))).thenAnswer(invocation -> invocation.getArgument(0));
        JobScoringService masterService = new JobScoringService(repository, resumes, codec);

        Job job = new Job();
        job.setTitle("Senior DevOps Engineer");
        job.setDescription("Required: AWS, Docker, Kubernetes and Terraform. Preferred: Azure.");
        job.setExperienceMin(4);
        job.setExperienceMax(8);
        job.setCountry("United Arab Emirates");

        JobScore score = masterService.scoreJob(job);

        assertEquals(new BigDecimal("100.00"), score.getTargetTitleScore());
        assertEquals(new BigDecimal("100.00"), score.getRequiredSkillsScore());
        assertEquals(new BigDecimal("0.00"), score.getPreferredSkillsScore());
        org.junit.jupiter.api.Assertions.assertTrue(score.getMatchedKeywords().contains("AWS"));
        org.junit.jupiter.api.Assertions.assertTrue(score.getMatchedKeywords().contains("Terraform"));
        assertEquals("Azure", score.getMissingKeywords());
        org.junit.jupiter.api.Assertions.assertTrue(score.getScore().compareTo(new BigDecimal("80.00")) > 0);
        org.junit.jupiter.api.Assertions.assertTrue(score.getScoringReason().contains("Active master v3"));
        org.junit.jupiter.api.Assertions.assertTrue(score.getScoringReason().contains("UAE location"));
        assertEquals("HIGH", score.getScoringConfidence());
    }

    @Test
    void capsIncompleteJobAtSeventyAndRemovesItFromHighScoreEligibility() {
        JobScoringService masterService = masterScoringService();
        Job job = new Job();
        job.setTitle("Senior DevOps Engineer");
        job.setDescription(" ");
        job.setExperienceMin(4);
        job.setExperienceMax(8);
        job.setCountry("United Arab Emirates");
        job.setStatus("HIGH_SCORE");

        JobScore score = masterService.scoreJob(job);

        assertEquals(new BigDecimal("70.00"), score.getScore());
        assertEquals("LOW", score.getScoringConfidence());
        assertEquals("Requirements unavailable", score.getMissingKeywords());
        assertEquals("NEW", job.getStatus());
        verify(jobs).save(job);
    }

    @Test
    void capsScoresAboveNinetyWithoutMultipleMeaningfulSkillMatches() {
        JobScoringService masterService = masterScoringService();
        Job job = new Job();
        job.setTitle("Senior DevOps Engineer");
        job.setDescription("Required DevOps engineer cloud platforms. DevOps engineer cloud platforms. DevOps engineer cloud platforms.");
        job.setExperienceMin(4);
        job.setExperienceMax(8);
        job.setCountry("United Arab Emirates");

        JobScore score = masterService.scoreJob(job);

        assertEquals("HIGH", score.getScoringConfidence());
        assertEquals(new BigDecimal("90.00"), score.getScore());
        assertEquals("DevOps", score.getMatchedKeywords());
    }

    @Test
    void classifiesZiinaInfrastructureSectionsAndAlternativesEvidenceFirst() {
        ResumeVersionRepository resumes = mock(ResumeVersionRepository.class);
        ResumeJsonCodec codec = new ResumeJsonCodec(new ObjectMapper());
        ParsedResume parsed = new ParsedResume("Candidate Example", "DevOps engineer building cloud platforms.",
                List.of(new ExperienceEntry("Example Corp", "DevOps Engineer", "Jan 2021 - Dec 2024",
                        List.of("Built AWS Kubernetes Terraform Docker CI/CD platforms with GitHub Actions, Prometheus and Grafana."))),
                List.of("AWS", "Kubernetes", "Terraform", "Docker", "CI/CD", "GitHub Actions", "Prometheus", "Grafana", "DevOps"),
                List.of(), List.of(), List.of());
        ResumeVersion master = new ResumeVersion(); master.setMasterResume(true); master.setVersionNumber(4);
        master.setParsedText("DevOps Engineer AWS Kubernetes Terraform Docker CI/CD GitHub Actions Prometheus Grafana");
        master.setStructuredExperience(codec.write(parsed));
        when(resumes.findFirstByMasterResumeTrue()).thenReturn(Optional.of(master));
        when(repository.findByJob(any(Job.class))).thenReturn(Optional.empty());
        when(repository.save(any(JobScore.class))).thenAnswer(invocation -> invocation.getArgument(0));
        JobScoringService scoring = new JobScoringService(repository, resumes, codec, jobs);
        Job job = new Job(); job.setTitle("Senior Platform Engineer (Infrastructure)"); job.setCountry("UAE");job.setCity("Dubai");job.setExperienceMin(5);
        job.setDescription("""
                AS A SENIOR PLATFORM ENGINEER (INFRASTRUCTURE) AT ZIINA YOU WILL:
                Own and continuously improve our CI/CD pipelines. Improve system observability, monitoring, and incident response.
                TO SUCCEED IN THIS ROLE, YOU LIKELY:
                Have 5+ years of experience in Platform Engineering, DevOps, or SRE.
                Have deep expertise with cloud services (ideally AWS) and infrastructure-as-code tools like Terraform, CloudFormation, or Pulumi.
                Are proficient with Docker, Kubernetes and understand microservices architecture.
                Have hands-on experience building CI/CD pipelines (e.g., GitHub Actions, Jenkins) and observability stacks (Prometheus, Grafana, DataDog, or similar).
                Are comfortable with on-call responsibilities and incident response practices. Use the latest AI tools.
                WHAT WOULD AMAZE US:
                Proven experience building infrastructure for fintech or other high-reliability platforms.
                Experience with scaling, latency, resilience, and multi-region architectures.
                A track record of building platform tooling that improves developer productivity.
                Experience leading infrastructure or platform initiatives in a fast-growing environment.
                Contributions to open-source infrastructure tools or community involvement.
                OUR TECH STACK:
                Typescript, Next.js, React, Kafka, Redis and Elasticsearch power company products.
                """);

        JobScore score = scoring.scoreJob(job);

        assertEquals(new BigDecimal("80.00"), score.getTargetTitleScore());
        assertEquals(new BigDecimal("63.64"), score.getRequiredSkillsScore());
        assertEquals(new BigDecimal("0.00"), score.getPreferredSkillsScore());
        assertEquals(new BigDecimal("55.47"), score.getScore());
        assertEquals(new BigDecimal("11.54"), score.getKeywordCoverageScore());
        org.junit.jupiter.api.Assertions.assertTrue(score.getMatchedKeywords().contains("Prometheus"));
        org.junit.jupiter.api.Assertions.assertTrue(score.getMatchedKeywords().contains("Grafana"));
        org.junit.jupiter.api.Assertions.assertTrue(score.getMatchedKeywords().contains("DevOps/Platform role family"));
        for (String contextual : List.of("TypeScript", "React", "Next.js", "Redis", "Kafka", "ELK", "Datadog", "SRE", "CloudFormation", "Pulumi"))
            org.junit.jupiter.api.Assertions.assertFalse(score.getMissingKeywords().contains(contextual), contextual);
        for (String genuine : List.of("Microservices", "Incident response", "On-call", "AI tools"))
            org.junit.jupiter.api.Assertions.assertTrue(score.getMissingKeywords().contains(genuine), genuine);
        for (String preferred : List.of("Fintech/high-reliability infrastructure",
                "Scale/latency/resilience/multi-region architecture", "Platform tooling",
                "Infrastructure/platform leadership", "Open-source/community contributions")) {
            org.junit.jupiter.api.Assertions.assertTrue(score.getMissingKeywords().contains(preferred), preferred);
        }
        org.junit.jupiter.api.Assertions.assertTrue(score.getScore().compareTo(new BigDecimal("75.00")) < 0);
    }

    @Test
    void authenticatedRescoreUpdatesOwnedScore() {
        authenticate("owner-a");
        ResumeVersionRepository resumes = mock(ResumeVersionRepository.class);
        ResumeJsonCodec codec = new ResumeJsonCodec(new ObjectMapper());
        ParsedResume parsed = new ParsedResume("Candidate Example", "DevOps engineer", List.of(),
                List.of("AWS", "Docker"), List.of(), List.of(), List.of());
        ResumeVersion master = new ResumeVersion();
        master.setMasterResume(true);
        master.setOwnerSubject("owner-a");
        master.setParsedText("DevOps engineer AWS Docker");
        master.setStructuredExperience(codec.write(parsed));
        Job job = new Job();
        job.setTitle("DevOps Engineer");
        job.setDescription("Required: AWS and Docker for this role and its production responsibilities.");
        JobScore existing = new JobScore();
        existing.setOwnerSubject("owner-a");
        existing.setJob(job);
        when(resumes.findFirstByMasterResumeTrueAndOwnerSubject("owner-a")).thenReturn(Optional.of(master));
        when(repository.findByJobAndOwnerSubject(job, "owner-a")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        JobScore result = new JobScoringService(repository, resumes, codec, jobs).scoreJob(job);

        assertEquals(existing, result);
        assertEquals("owner-a", result.getOwnerSubject());
        verify(repository).save(existing);
    }

    private void authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("synthetic").header("alg", "none").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private JobScoringService masterScoringService() {
        ResumeVersionRepository resumes = mock(ResumeVersionRepository.class);
        ResumeJsonCodec codec = new ResumeJsonCodec(new ObjectMapper());
        ParsedResume parsed = new ParsedResume(
                "Jane Example",
                "DevOps engineer building reliable cloud platforms.",
                List.of(new ExperienceEntry("Example Corp", "DevOps Engineer", "Jan 2020 - Present",
                        List.of("Built AWS Docker Kubernetes Terraform platforms."))),
                List.of("AWS", "Docker", "Kubernetes", "Terraform", "DevOps"),
                List.of(), List.of(), List.of()
        );
        ResumeVersion master = new ResumeVersion();
        master.setMasterResume(true);
        master.setVersionNumber(3);
        master.setParsedText("DevOps Engineer Example Corp Jan 2020 - Present AWS Docker Kubernetes Terraform cloud platforms");
        master.setStructuredExperience(codec.write(parsed));
        when(resumes.findFirstByMasterResumeTrue()).thenReturn(Optional.of(master));
        when(repository.findByJob(any(Job.class))).thenReturn(Optional.empty());
        when(repository.save(any(JobScore.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new JobScoringService(repository, resumes, codec, jobs);
    }

    private Job targetJob() {
        Job job = new Job();
        job.setTitle("Cloud Platform Engineer");
        job.setDescription("AWS Azure Kubernetes Docker Terraform GitHub Actions CI/CD Jenkins Linux");
        job.setExperienceMin(5);
        job.setExperienceMax(8);
        job.setCity("Dubai");
        job.setSalaryMax(BigDecimal.valueOf(30_000));
        job.setSalaryCurrency("AED");
        return job;
    }
}
