package career.assistant.job.repository;

import career.assistant.job.entity.Job;
import career.assistant.scraper.config.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    long countByStatus(String status);

    Optional<Job> findBySourceAndSourceJobId(
            JobSource source,
            String sourceJobId
    );

    boolean existsBySourceAndSourceJobId(
            JobSource source,
            String sourceJobId
    );
}
