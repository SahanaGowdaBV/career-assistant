package career.assistant.document.repository;

import career.assistant.document.entity.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, UUID> {

    List<ResumeVersion> findAllByOrderByVersionNumberDesc();

    Optional<ResumeVersion> findFirstByMasterResumeTrue();

    Optional<ResumeVersion> findTopByOrderByVersionNumberDesc();

    Optional<ResumeVersion> findFirstByJobIdAndCustomizedTrue(UUID jobId);

    List<ResumeVersion> findByCreatedAtBefore(java.time.OffsetDateTime cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ResumeVersion resume set resume.masterResume = false where resume.masterResume = true")
    int deactivateAllMasters();
}
