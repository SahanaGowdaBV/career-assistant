package career.assistant.job.controller;

import career.assistant.job.entity.Job;
import career.assistant.job.service.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public ResponseEntity<List<Job>> getAllJobs() {
        return ResponseEntity.ok(jobService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJobById(@PathVariable UUID id) {
        return jobService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Job> createJob(@RequestBody Job job) {

        if (job.getSource() == null || job.getSourceJobId() == null) {
            return ResponseEntity.badRequest().build();
        }

        if (jobService.existsBySourceAndSourceJobId(
                job.getSource(),
                job.getSourceJobId()
        )) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(jobService.save(job));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {

        if (jobService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        jobService.deleteById(id);

        return ResponseEntity.noContent().build();
    }
}
