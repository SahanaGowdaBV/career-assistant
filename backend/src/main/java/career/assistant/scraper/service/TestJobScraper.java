package career.assistant.scraper.service;

import career.assistant.scraper.config.JobSource;
import career.assistant.scraper.model.ScrapedJob;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class TestJobScraper implements JobScraper {

    @Override
    public JobSource getSource() {
        return JobSource.COMPANY_CAREER_PAGE;
    }

    @Override
    public List<ScrapedJob> scrape() {

        ScrapedJob job = new ScrapedJob();

        job.setTitle("Senior DevOps Engineer");

        job.setCompanyName("Test UAE Company");

        job.setDescription("""
                Senior DevOps Engineer responsible for AWS,
                Azure, Kubernetes, Docker, Terraform,
                GitHub Actions, CI/CD, Jenkins and Linux.
                """);

        job.setLocation("Dubai, United Arab Emirates");
        job.setCountry("UAE");
        job.setCity("Dubai");

        job.setExperienceMin(5);
        job.setExperienceMax(8);

        job.setSalaryMin(BigDecimal.valueOf(22000));
        job.setSalaryMax(BigDecimal.valueOf(28000));
        job.setSalaryCurrency("AED");

        job.setEmploymentType("FULL_TIME");

        job.setJobUrl("https://example.com/jobs/test-devops");

        job.setSourceJobId("TEST-002");

        job.setSource(getSource());

        job.setPostedAt(
                OffsetDateTime.now().minusHours(6)
        );

        return List.of(job);
    }
}
