package career.assistant.job.repository;

import career.assistant.job.entity.Job;
import career.assistant.scraper.config.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    boolean existsByJobUrlIn(java.util.Collection<String> jobUrls);

    boolean existsByCanonicalUrlIgnoreCase(String canonicalUrl);

    @Query("select j from Job j where j.ownerSubject is null or j.ownerSubject = :owner")
    List<Job> findVisibleTo(@Param("owner") String owner);

    @Query("select j from Job j where j.id = :id and (j.ownerSubject is null or j.ownerSubject = :owner)")
    Optional<Job> findVisibleById(@Param("id") UUID id, @Param("owner") String owner);

    List<Job> findByStatusInAndUpdatedAtBefore(java.util.Collection<String> statuses, java.time.OffsetDateTime cutoff);
}
