package career.assistant.document.repository;

import career.assistant.document.entity.CoverLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoverLetterRepository extends JpaRepository<CoverLetter,UUID> {
    Optional<CoverLetter> findFirstByJobId(UUID jobId);
    List<CoverLetter> findAllByOrderByCreatedAtDesc();
    List<CoverLetter> findByCreatedAtBefore(OffsetDateTime cutoff);
}
