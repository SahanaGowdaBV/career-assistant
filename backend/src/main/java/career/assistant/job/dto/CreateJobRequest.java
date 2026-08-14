package career.assistant.job.dto;

import career.assistant.scraper.config.JobSource;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CreateJobRequest {

    @NotBlank
    @Size(max = 500)
    private String title;

    @NotNull
    private UUID companyId;

    private String description;

    @Size(max = 500)
    private String location;

    @Size(max = 100)
    private String country;

    @Size(max = 150)
    private String city;

    @Size(max = 100)
    private String employmentType;

    @PositiveOrZero
    private Integer experienceMin;

    @PositiveOrZero
    private Integer experienceMax;

    @PositiveOrZero
    private BigDecimal salaryMin;

    @PositiveOrZero
    private BigDecimal salaryMax;

    @Size(max = 10)
    private String salaryCurrency;

    @NotNull
    private JobSource source;

    @Size(max = 500)
    @NotBlank
    private String sourceJobId;

    @NotBlank
    @Size(max = 1000)
    private String jobUrl;

    private OffsetDateTime postedAt;

    @Size(max = 50)
    private String status;

    @AssertTrue(message = "experienceMin must be less than or equal to experienceMax")
    public boolean isExperienceRangeValid() {
        return experienceMin == null || experienceMax == null || experienceMin <= experienceMax;
    }

    @AssertTrue(message = "salaryMin must be less than or equal to salaryMax")
    public boolean isSalaryRangeValid() {
        return salaryMin == null || salaryMax == null || salaryMin.compareTo(salaryMax) <= 0;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public Integer getExperienceMin() {
        return experienceMin;
    }

    public void setExperienceMin(Integer experienceMin) {
        this.experienceMin = experienceMin;
    }

    public Integer getExperienceMax() {
        return experienceMax;
    }

    public void setExperienceMax(Integer experienceMax) {
        this.experienceMax = experienceMax;
    }

    public BigDecimal getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(BigDecimal salaryMin) {
        this.salaryMin = salaryMin;
    }

    public BigDecimal getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(BigDecimal salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public void setSalaryCurrency(String salaryCurrency) {
        this.salaryCurrency = salaryCurrency;
    }

    public JobSource getSource() {
        return source;
    }

    public void setSource(JobSource source) {
        this.source = source;
    }

    public String getSourceJobId() {
        return sourceJobId;
    }

    public void setSourceJobId(String sourceJobId) {
        this.sourceJobId = sourceJobId;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public OffsetDateTime getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(OffsetDateTime postedAt) {
        this.postedAt = postedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
