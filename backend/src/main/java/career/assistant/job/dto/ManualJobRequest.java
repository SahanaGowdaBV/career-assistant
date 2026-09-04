package career.assistant.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualJobRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank @Size(max = 255) String company,
        @NotBlank @Size(max = 500) String location,
        @Size(max = 500) String experienceText,
        @NotBlank @Size(max = 50000) String description,
        @NotBlank @Size(max = 1000) String applicationUrl,
        @NotBlank @Size(max = 150) String sourcePortal
) {}
