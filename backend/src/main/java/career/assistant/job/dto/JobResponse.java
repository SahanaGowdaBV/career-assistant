package career.assistant.job.dto;

import career.assistant.scraper.config.JobSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String title,
        UUID companyId,
        String description,
        String location,
        String country,
        String city,
        String employmentType,
        Integer experienceMin,
        Integer experienceMax,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        JobSource source,
        String sourceJobId,
        String jobUrl,
        OffsetDateTime postedAt,
        OffsetDateTime scrapedAt,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
