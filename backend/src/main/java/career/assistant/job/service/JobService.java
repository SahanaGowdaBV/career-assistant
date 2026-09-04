package career.assistant.job.service;

import career.assistant.api.ResourceNotFoundException;
import career.assistant.job.entity.Job;
import career.assistant.job.exception.DuplicateJobException;
import career.assistant.job.repository.JobRepository;
import career.assistant.scraper.config.JobSource;
import career.assistant.security.AuthenticatedOwner;
import org.springframework.security.core.context.SecurityContextHolder;
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
        String owner = currentOwner();
        return owner == null ? jobRepository.findAll() : jobRepository.findVisibleTo(owner);
    }

    public Optional<Job> findById(UUID id) {
        String owner = currentOwner();
        return owner == null ? jobRepository.findById(id) : jobRepository.findVisibleById(id, owner);
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

    public boolean existsByCanonicalUrl(String canonicalUrl) { return jobRepository.existsByCanonicalUrlIgnoreCase(canonicalUrl); }

    public static List<String> urlVariants(String canonicalUrl) {
        int queryIndex = canonicalUrl.indexOf('?');
        String base = queryIndex < 0 ? canonicalUrl : canonicalUrl.substring(0, queryIndex);
        String query = queryIndex < 0 ? "" : canonicalUrl.substring(queryIndex);
        return List.of(canonicalUrl, base.endsWith("/") ? base.substring(0, base.length() - 1) + query : base + "/" + query);
    }

    public void deleteById(UUID id) {
        jobRepository.delete(findRequired(id));
    }

    public Page<Job> search(Specification<Job> specification, Pageable pageable) {
        String owner = currentOwner();
        if (owner == null) return jobRepository.findAll(specification, pageable);
        Specification<Job> visibleToOwner = (root, query, cb) -> cb.or(
                cb.isNull(root.get("ownerSubject")),
                cb.equal(root.get("ownerSubject"), owner)
        );
        return jobRepository.findAll(visibleToOwner.and(specification), pageable);
    }

    public Job updateStatus(UUID id, String status) {
        java.util.Set<String> allowed = java.util.Set.of("NEW", "HIGH_SCORE", "PENDING_REVIEW", "READY_TO_APPLY", "AUTO_APPLIED", "MANUALLY_APPLIED", "FAILED", "REJECTED", "ARCHIVED");
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("Unsupported job status: " + status);
        Job job = findRequired(id);
        job.setStatus(normalized);
        return jobRepository.save(job);
    }

    private String currentOwner() {
        return SecurityContextHolder.getContext().getAuthentication() == null ? null : AuthenticatedOwner.required();
    }
}
