package career.assistant.job.mapper;

import career.assistant.job.dto.CreateJobRequest;
import career.assistant.job.dto.JobResponse;
import career.assistant.job.entity.Job;

public final class JobMapper {

    private JobMapper() {
    }

    public static Job toEntity(CreateJobRequest request) {
        Job job = new Job();
        job.setTitle(request.getTitle());
        job.setCompanyId(request.getCompanyId());
        job.setDescription(request.getDescription());
        job.setLocation(request.getLocation());
        job.setCountry(request.getCountry());
        job.setCity(request.getCity());
        job.setEmploymentType(request.getEmploymentType());
        job.setExperienceMin(request.getExperienceMin());
        job.setExperienceMax(request.getExperienceMax());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setSalaryCurrency(request.getSalaryCurrency());
        job.setSource(request.getSource());
        job.setSourceJobId(request.getSourceJobId());
        job.setJobUrl(request.getJobUrl());
        job.setPostedAt(request.getPostedAt());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            job.setStatus(request.getStatus());
        }
        return job;
    }

    public static JobResponse toResponse(Job job) {
        return toResponse(job, null);
    }

    public static JobResponse toResponse(Job job, String companyName) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getCompanyId(),
                companyName,
                job.getDescription(),
                job.getLocation(),
                job.getCountry(),
                job.getCity(),
                job.getEmploymentType(),
                job.getExperienceMin(),
                job.getExperienceMax(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getSource(),
                job.getSourceJobId(),
                job.getJobUrl(),
                job.getPostedAt(),
                job.getScrapedAt(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
