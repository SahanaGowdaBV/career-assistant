package career.assistant.job.service;

import career.assistant.job.entity.Job;
import career.assistant.job.exception.DuplicateJobException;
import career.assistant.job.repository.JobRepository;
import career.assistant.scraper.config.JobSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobService jobService = new JobService(jobRepository);

    @Test
    void createsUniqueJob() {
        Job job = job(JobSource.LINKEDIN, "source-123");
        when(jobRepository.existsBySourceAndSourceJobId(JobSource.LINKEDIN, "source-123"))
                .thenReturn(false);
        when(jobRepository.save(job)).thenReturn(job);

        assertSame(job, jobService.create(job));
        verify(jobRepository).save(job);
    }

    @Test
    void rejectsDuplicateBeforeInsert() {
        Job job = job(JobSource.LINKEDIN, "source-123");
        when(jobRepository.existsBySourceAndSourceJobId(JobSource.LINKEDIN, "source-123"))
                .thenReturn(true);

        assertThrows(DuplicateJobException.class, () -> jobService.create(job));
        verify(jobRepository, never()).save(job);
    }

    private Job job(JobSource source, String sourceJobId) {
        Job job = new Job();
        job.setSource(source);
        job.setSourceJobId(sourceJobId);
        return job;
    }
}
