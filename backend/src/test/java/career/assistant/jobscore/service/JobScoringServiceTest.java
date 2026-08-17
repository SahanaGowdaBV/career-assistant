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
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
