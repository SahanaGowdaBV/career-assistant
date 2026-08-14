package career.assistant.scraper.controller;

import career.assistant.job.dto.JobResponse;
import career.assistant.job.mapper.JobMapper;
import career.assistant.job.service.JobImportService;
import career.assistant.scraper.service.JobScraper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scraper")
public class ScraperController {

    private final JobScraper jobScraper;
    private final JobImportService jobImportService;

    public ScraperController(
            JobScraper jobScraper,
            JobImportService jobImportService
    ) {
        this.jobScraper = jobScraper;
        this.jobImportService = jobImportService;
    }

    @PostMapping("/run")
    public ResponseEntity<List<JobResponse>> runScraper() {

        return ResponseEntity.ok(jobImportService.importJobs(
                        jobScraper.scrape()
                ).stream()
                .map(JobMapper::toResponse)
                .toList());
    }
}
