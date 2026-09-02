package career.assistant.application.audit;
import org.springframework.data.jpa.repository.*; import java.time.OffsetDateTime; import java.util.*;
public interface SubmissionAttemptRepository extends JpaRepository<SubmissionAttempt,UUID> {
 Optional<SubmissionAttempt> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
 Optional<SubmissionAttempt> findByIdempotencyKeyAndOwnerSubject(String key,String owner);
 Optional<SubmissionAttempt> findFirstBySourceFingerprintAndStateIn(String source,Collection<SubmissionAttemptState> states);
 boolean existsByApplicationIdAndStateIn(UUID applicationId,Collection<SubmissionAttemptState> states);
 List<SubmissionAttempt> findByOwnerSubjectOrderByCreatedAtDesc(String owner);
 long countByOwnerSubjectAndStateAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(String owner,SubmissionAttemptState state,OffsetDateTime from,OffsetDateTime to);
}
