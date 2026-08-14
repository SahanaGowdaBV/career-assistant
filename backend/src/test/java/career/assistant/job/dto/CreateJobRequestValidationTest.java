package career.assistant.job.dto;

import career.assistant.scraper.config.JobSource;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateJobRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidRequest() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void rejectsMissingRequiredFieldsAndInvertedRanges() {
        CreateJobRequest request = new CreateJobRequest();
        request.setTitle(" ");
        request.setExperienceMin(8);
        request.setExperienceMax(3);
        request.setSalaryMin(BigDecimal.valueOf(30_000));
        request.setSalaryMax(BigDecimal.valueOf(20_000));

        var violations = validator.validate(request);

        assertEquals(7, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sourceJobId")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("experienceMin")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("salaryMin")));
    }

    private CreateJobRequest validRequest() {
        CreateJobRequest request = new CreateJobRequest();
        request.setTitle("Platform Engineer");
        request.setCompanyId(UUID.randomUUID());
        request.setSource(JobSource.LINKEDIN);
        request.setSourceJobId("linkedin-123");
        request.setJobUrl("https://example.test/jobs/123");
        request.setExperienceMin(4);
        request.setExperienceMax(7);
        return request;
    }
}
