package career.assistant.job.controller;

import career.assistant.job.dto.CreateJobRequest;
import career.assistant.job.dto.JobResponse;
import career.assistant.job.mapper.JobMapper;
import career.assistant.job.service.JobService;
import jakarta.validation.Valid;
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
    public ResponseEntity<List<JobResponse>> getAllJobs() {
        return ResponseEntity.ok(jobService.findAll().stream()
                .map(JobMapper::toResponse)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJobById(@PathVariable UUID id) {
        return ResponseEntity.ok(JobMapper.toResponse(jobService.findRequired(id)));
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @Valid @RequestBody CreateJobRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(JobMapper.toResponse(
                        jobService.create(JobMapper.toEntity(request))
                ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        jobService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
