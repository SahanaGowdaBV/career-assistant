package career.assistant.document.dto;

import career.assistant.document.model.ParsedResume;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResumeDetailsResponse(
        UUID id,
        String filename,
        OffsetDateTime uploadedAt,
        int version,
        String status,
        boolean master,
        boolean customized,
        String contentType,
        long fileSize,
        String checksum,
        String parsedText,
        ParsedResume parsed,
        UUID sourceResumeId,
        UUID jobId,
        String customizationSummary
) {
}
