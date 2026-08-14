package career.assistant.jobscore.controller;

import career.assistant.job.entity.Job;
import career.assistant.job.service.JobService;
import career.assistant.jobscore.entity.JobScore;
import career.assistant.jobscore.service.JobScoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobScoreController {

    private final JobService jobService;
    private final JobScoringService jobScoringService;

    public JobScoreController(
            JobService jobService,
            JobScoringService jobScoringService
    ) {
        this.jobService = jobService;
        this.jobScoringService = jobScoringService;
    }

    @PostMapping("/{id}/score")
    public ResponseEntity<JobScore> scoreJob(
            @PathVariable UUID id
    ) {

        return jobService.findById(id)
                .map(job -> ResponseEntity.ok(
                        jobScoringService.scoreJob(job)
                ))
                .orElse(ResponseEntity.notFound().build());
    }
}
