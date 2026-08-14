package career.assistant.job.service;

import career.assistant.company.entity.Company;
import career.assistant.company.service.CompanyService;
import career.assistant.job.entity.Job;
import career.assistant.scraper.model.ScrapedJob;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobImportService {

    private final JobService jobService;
    private final CompanyService companyService;

    public JobImportService(
            JobService jobService,
            CompanyService companyService
    ) {
        this.jobService = jobService;
        this.companyService = companyService;
    }

    public Job importJob(ScrapedJob scrapedJob) {

        // 1. Check whether this job already exists
        if (jobService.existsBySourceAndSourceJobId(
                scrapedJob.getSource(),
                scrapedJob.getSourceJobId()
        )) {
            return jobService.findBySourceAndSourceJobId(
                    scrapedJob.getSource(),
                    scrapedJob.getSourceJobId()
            ).orElseThrow();
        }

        // 2. Find or create company
        Company company = companyService.findOrCreate(
                scrapedJob.getCompanyName()
        );

        // 3. Convert ScrapedJob → Job entity
        Job job = new Job();

        job.setTitle(scrapedJob.getTitle());
        job.setCompanyId(company.getId());

        job.setDescription(scrapedJob.getDescription());

        job.setLocation(scrapedJob.getLocation());
        job.setCountry(scrapedJob.getCountry());
        job.setCity(scrapedJob.getCity());

        job.setEmploymentType(scrapedJob.getEmploymentType());
        job.setExperienceMin(scrapedJob.getExperienceMin());
        job.setExperienceMax(scrapedJob.getExperienceMax());
       
        job.setSalaryMin(scrapedJob.getSalaryMin());
        job.setSalaryMax(scrapedJob.getSalaryMax());
        job.setSalaryCurrency(scrapedJob.getSalaryCurrency());

        job.setJobUrl(scrapedJob.getJobUrl());
        job.setSourceJobId(scrapedJob.getSourceJobId());
        job.setSource(scrapedJob.getSource());

        job.setPostedAt(scrapedJob.getPostedAt());

        job.setStatus("NEW");

        // 4. Save
        return jobService.save(job);
    }

    public List<Job> importJobs(List<ScrapedJob> scrapedJobs) {

        return scrapedJobs.stream()
                .map(this::importJob)
                .toList();
    }
}
