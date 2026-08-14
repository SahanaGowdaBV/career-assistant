package career.assistant.scraper.controller;

import career.assistant.job.entity.Job;
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
    public ResponseEntity<List<Job>> runScraper() {

        List<Job> jobs = jobImportService.importJobs(
                jobScraper.scrape()
        );

        return ResponseEntity.ok(jobs);
    }
}
