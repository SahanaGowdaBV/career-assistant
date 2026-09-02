package career.assistant.document.repository;

import career.assistant.document.entity.CoverLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoverLetterRepository extends JpaRepository<CoverLetter,UUID> {
    Optional<CoverLetter> findFirstByJobIdOrderByCreatedAtDesc(UUID jobId);
    Optional<CoverLetter> findFirstByJobIdAndOwnerSubjectOrderByCreatedAtDesc(UUID jobId,String ownerSubject);
    Optional<CoverLetter> findByIdAndOwnerSubject(UUID id,String ownerSubject);
    List<CoverLetter> findAllByOrderByCreatedAtDesc();
    List<CoverLetter> findAllByOwnerSubjectOrderByCreatedAtDesc(String ownerSubject);
    List<CoverLetter> findByCreatedAtBefore(OffsetDateTime cutoff);
}
