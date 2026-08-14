package career.assistant.jobscore.mapper;

import career.assistant.jobscore.dto.JobScoreResponse;
import career.assistant.jobscore.entity.JobScore;

public final class JobScoreMapper {

    private JobScoreMapper() {
    }

    public static JobScoreResponse toResponse(JobScore jobScore) {
        return new JobScoreResponse(
                jobScore.getId(),
                jobScore.getJob().getId(),
                jobScore.getScore(),
                jobScore.getSkillsScore(),
                jobScore.getExperienceScore(),
                jobScore.getLocationScore(),
                jobScore.getSalaryScore(),
                jobScore.getTargetTitleScore(),
                jobScore.getRequiredSkillsScore(),
                jobScore.getPreferredSkillsScore(),
                jobScore.getKeywordCoverageScore(),
                jobScore.getMatchedKeywords(),
                jobScore.getMissingKeywords(),
                jobScore.getScoringReason(),
                jobScore.getScoredAt()
        );
    }
}
