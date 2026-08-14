package career.assistant.jobscore.repository;

import career.assistant.job.entity.Job;
import career.assistant.jobscore.entity.JobScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobScoreRepository extends JpaRepository<JobScore, UUID> {

    Optional<JobScore> findByJob(Job job);

    boolean existsByJob(Job job);
}
