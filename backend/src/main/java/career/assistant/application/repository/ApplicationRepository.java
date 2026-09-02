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
    Optional<Application> findByJobIdAndOwnerSubject(UUID jobId, String ownerSubject);
    Optional<Application> findByIdAndOwnerSubject(UUID id, String ownerSubject);
    List<Application> findAllByOwnerSubject(String ownerSubject);

    boolean existsByJobId(UUID jobId);

    List<Application> findByStatus(ApplicationStatus status);
    List<Application> findByStatusAndOwnerSubject(ApplicationStatus status, String ownerSubject);
    List<Application> findByStatusAndOwnerSubjectIsNotNull(ApplicationStatus status);
    long countByStatus(ApplicationStatus status);

    long countByStatusAndAppliedAtBetween(ApplicationStatus status, java.time.OffsetDateTime start, java.time.OffsetDateTime end);

    List<Application> findByUpdatedAtBefore(java.time.OffsetDateTime cutoff);

    boolean existsByResumeVersionIdAndStatusIn(UUID resumeVersionId, java.util.Collection<ApplicationStatus> statuses);
    boolean existsByCoverLetterIdAndStatusIn(UUID coverLetterId, java.util.Collection<ApplicationStatus> statuses);
}
