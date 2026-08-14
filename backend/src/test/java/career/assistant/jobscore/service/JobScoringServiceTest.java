package career.assistant.jobscore.service;

import career.assistant.job.entity.Job;
import career.assistant.jobscore.entity.JobScore;
import career.assistant.jobscore.repository.JobScoreRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobScoringServiceTest {

    private final JobScoreRepository repository = mock(JobScoreRepository.class);
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
