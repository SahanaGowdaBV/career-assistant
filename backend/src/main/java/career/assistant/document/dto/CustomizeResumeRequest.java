package career.assistant.document.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CustomizeResumeRequest(@NotNull UUID jobId) {
}
