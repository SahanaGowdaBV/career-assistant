package career.assistant.job.service;

import career.assistant.api.ResourceNotFoundException;
import career.assistant.job.entity.Job;
import career.assistant.job.exception.DuplicateJobException;
import career.assistant.job.repository.JobRepository;
import career.assistant.scraper.config.JobSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@Service
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public Job save(Job job) {
        return jobRepository.save(job);
    }

    public Job create(Job job) {
        if (jobRepository.existsBySourceAndSourceJobId(
                job.getSource(),
                job.getSourceJobId()
        )) {
            throw new DuplicateJobException(
                    "A job with source " + job.getSource()
                            + " and sourceJobId " + job.getSourceJobId()
                            + " already exists"
            );
        }
        return jobRepository.save(job);
    }

    public List<Job> findAll() {
        return jobRepository.findAll();
    }

    public Optional<Job> findById(UUID id) {
        return jobRepository.findById(id);
    }

    public Job findRequired(UUID id) {
        return findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Job " + id + " was not found")
        );
    }

    public Optional<Job> findBySourceAndSourceJobId(
            JobSource source,
            String sourceJobId
    ) {
        return jobRepository.findBySourceAndSourceJobId(
                source,
                sourceJobId
        );
    }

    public boolean existsBySourceAndSourceJobId(
            JobSource source,
            String sourceJobId
    ) {
        return jobRepository.existsBySourceAndSourceJobId(
                source,
                sourceJobId
        );
    }

    public boolean existsByJobUrlIn(java.util.Collection<String> jobUrls) {
        return jobRepository.existsByJobUrlIn(jobUrls);
    }

    public void deleteById(UUID id) {
        jobRepository.delete(findRequired(id));
    }

    public Page<Job> search(Specification<Job> specification, Pageable pageable) {
        return jobRepository.findAll(specification, pageable);
    }

    public Job updateStatus(UUID id, String status) {
        java.util.Set<String> allowed = java.util.Set.of("NEW", "HIGH_SCORE", "PENDING_REVIEW", "READY_TO_APPLY", "AUTO_APPLIED", "MANUALLY_APPLIED", "FAILED", "REJECTED", "ARCHIVED");
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("Unsupported job status: " + status);
        Job job = findRequired(id);
        job.setStatus(normalized);
        return jobRepository.save(job);
    }
}
