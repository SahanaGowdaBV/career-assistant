package career.assistant.jobscore.entity;

import career.assistant.job.entity.Job;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_scores")
public class JobScore {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_subject", length = 255)
    private String ownerSubject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "job_id",
        nullable = false,
        unique = false
    )
    private Job job;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "skills_score", precision = 5, scale = 2)
    private BigDecimal skillsScore;

    @Column(name = "experience_score", precision = 5, scale = 2)
    private BigDecimal experienceScore;

    @Column(name = "location_score", precision = 5, scale = 2)
    private BigDecimal locationScore;

    @Column(name = "salary_score", precision = 5, scale = 2)
    private BigDecimal salaryScore;

    @Column(name = "target_title_score", precision = 5, scale = 2)
    private BigDecimal targetTitleScore;

    @Column(name = "required_skills_score", precision = 5, scale = 2)
    private BigDecimal requiredSkillsScore;

    @Column(name = "preferred_skills_score", precision = 5, scale = 2)
    private BigDecimal preferredSkillsScore;

    @Column(name = "keyword_coverage_score", precision = 5, scale = 2)
    private BigDecimal keywordCoverageScore;

    @Column(name = "matched_keywords", columnDefinition = "TEXT")
    private String matchedKeywords;

    @Column(name = "missing_keywords", columnDefinition = "TEXT")
    private String missingKeywords;

    @Column(name = "scoring_reason", columnDefinition = "TEXT")
    private String scoringReason;

    @Transient
    private String scoringConfidence;

    @Column(name = "scored_at", nullable = false)
    private OffsetDateTime scoredAt;

    @PrePersist
    protected void onCreate() {
        if (scoredAt == null) {
            scoredAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        scoredAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }
    public String getOwnerSubject() { return ownerSubject; }
    public void setOwnerSubject(String ownerSubject) { this.ownerSubject = ownerSubject; }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getSkillsScore() {
        return skillsScore;
    }

    public void setSkillsScore(BigDecimal skillsScore) {
        this.skillsScore = skillsScore;
    }

    public BigDecimal getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(BigDecimal experienceScore) {
        this.experienceScore = experienceScore;
    }

    public BigDecimal getLocationScore() {
        return locationScore;
    }

    public void setLocationScore(BigDecimal locationScore) {
        this.locationScore = locationScore;
    }

    public BigDecimal getSalaryScore() {
        return salaryScore;
    }

    public void setSalaryScore(BigDecimal salaryScore) {
        this.salaryScore = salaryScore;
    }

    public BigDecimal getTargetTitleScore() { return targetTitleScore; }
    public void setTargetTitleScore(BigDecimal targetTitleScore) { this.targetTitleScore = targetTitleScore; }
    public BigDecimal getRequiredSkillsScore() { return requiredSkillsScore; }
    public void setRequiredSkillsScore(BigDecimal requiredSkillsScore) { this.requiredSkillsScore = requiredSkillsScore; }
    public BigDecimal getPreferredSkillsScore() { return preferredSkillsScore; }
    public void setPreferredSkillsScore(BigDecimal preferredSkillsScore) { this.preferredSkillsScore = preferredSkillsScore; }
    public BigDecimal getKeywordCoverageScore() { return keywordCoverageScore; }
    public void setKeywordCoverageScore(BigDecimal keywordCoverageScore) { this.keywordCoverageScore = keywordCoverageScore; }

    public String getMatchedKeywords() {
        return matchedKeywords;
    }

    public void setMatchedKeywords(String matchedKeywords) {
        this.matchedKeywords = matchedKeywords;
    }

    public String getMissingKeywords() {
        return missingKeywords;
    }

    public void setMissingKeywords(String missingKeywords) {
        this.missingKeywords = missingKeywords;
    }

    public String getScoringReason() {
        return scoringReason;
    }

    public void setScoringReason(String scoringReason) {
        this.scoringReason = scoringReason;
    }

    public String getScoringConfidence() {
        return scoringConfidence;
    }

    public void setScoringConfidence(String scoringConfidence) {
        this.scoringConfidence = scoringConfidence;
    }

    public OffsetDateTime getScoredAt() {
        return scoredAt;
    }
}
