package career.assistant.document.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ResumeSummaryResponse(
        UUID id,
        String filename,
        OffsetDateTime uploadedAt,
        int version,
        String status,
        boolean master,
        boolean customized,
        List<String> parsedSkills,
        String contentType,
        long fileSize
) {
}
