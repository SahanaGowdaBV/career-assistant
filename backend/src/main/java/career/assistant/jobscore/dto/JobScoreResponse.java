package career.assistant.jobscore.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record JobScoreResponse(
        UUID id,
        UUID jobId,
        BigDecimal score,
        BigDecimal skillsScore,
        BigDecimal experienceScore,
        BigDecimal locationScore,
        BigDecimal salaryScore,
        BigDecimal targetTitleScore,
        BigDecimal requiredSkillsScore,
        BigDecimal preferredSkillsScore,
        BigDecimal keywordCoverageScore,
        String matchedKeywords,
        String missingKeywords,
        String scoringReason,
        OffsetDateTime scoredAt
) {
}
