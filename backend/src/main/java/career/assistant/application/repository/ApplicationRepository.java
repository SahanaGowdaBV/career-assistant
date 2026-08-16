package career.assistant.application.repository;

import career.assistant.application.entity.Application;
import career.assistant.application.entity.ApplicationStatus;
import career.assistant.job.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository
        extends JpaRepository<Application, UUID> {

    Optional<Application> findByJob(Job job);

    Optional<Application> findByJobId(UUID jobId);

    boolean existsByJobId(UUID jobId);

    List<Application> findByStatus(ApplicationStatus status);
    long countByStatus(ApplicationStatus status);

    long countByStatusAndAppliedAtBetween(ApplicationStatus status, java.time.OffsetDateTime start, java.time.OffsetDateTime end);

    List<Application> findByUpdatedAtBefore(java.time.OffsetDateTime cutoff);

    boolean existsByResumeVersionIdAndStatusIn(UUID resumeVersionId, java.util.Collection<ApplicationStatus> statuses);
    boolean existsByCoverLetterIdAndStatusIn(UUID coverLetterId, java.util.Collection<ApplicationStatus> statuses);
}
