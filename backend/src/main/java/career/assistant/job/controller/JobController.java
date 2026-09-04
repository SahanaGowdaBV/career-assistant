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
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import career.assistant.job.entity.Job;
import career.assistant.company.repository.CompanyRepository;
import career.assistant.job.dto.ManualJobRequest;
import career.assistant.job.service.ManualJobImportService;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final CompanyRepository companies;
    private final ManualJobImportService manualJobs;

    public JobController(JobService jobService, CompanyRepository companies, ManualJobImportService manualJobs) {
        this.jobService = jobService;
        this.companies = companies;
        this.manualJobs = manualJobs;
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> getAllJobs() {
        return ResponseEntity.ok(jobService.findAll().stream()
                .map(this::response)
                .toList());
    }

    @GetMapping("/page")
    public Page<JobResponse> getJobs(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "postedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        Set<String> allowedSorts = Set.of("postedAt", "createdAt", "title", "status");
        String safeSort = allowedSorts.contains(sort) ? sort : "postedAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var spec = (org.springframework.data.jpa.domain.Specification<Job>) (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null && !search.isBlank()) {
                String q = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), q), cb.like(cb.lower(root.get("description")), q)));
            }
            if (status != null && !status.isBlank()) predicates.add(cb.equal(root.get("status"), status));
            if (city != null && !city.isBlank()) predicates.add(cb.equal(cb.lower(root.get("city")), city.toLowerCase()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return jobService.search(spec, PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100), Sort.by(dir, safeSort)))
                .map(this::response);
    }

    @PatchMapping("/{id}/status")
    public JobResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return response(jobService.updateStatus(id, request.status()));
    }

    public record StatusRequest(@jakarta.validation.constraints.NotBlank String status) {}

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJobById(@PathVariable UUID id) {
        return ResponseEntity.ok(response(jobService.findRequired(id)));
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @Valid @RequestBody CreateJobRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response(jobService.create(JobMapper.toEntity(request))));
    }

    @PostMapping("/manual")
    public ResponseEntity<JobResponse> createManualJob(@Valid @RequestBody ManualJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(response(manualJobs.create(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        jobService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private JobResponse response(Job job) {
        String companyName = companies.findById(job.getCompanyId()).map(value -> value.getName()).orElse(null);
        return JobMapper.toResponse(job, companyName);
    }
}
