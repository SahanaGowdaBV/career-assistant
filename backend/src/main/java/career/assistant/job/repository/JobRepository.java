package career.assistant.job.repository;

import career.assistant.job.entity.Job;
import career.assistant.scraper.config.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findBySourceAndSourceJobId(
            JobSource source,
            String sourceJobId
    );

    boolean existsBySourceAndSourceJobId(
            JobSource source,
            String sourceJobId
    );
}
