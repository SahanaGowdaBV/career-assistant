package career.assistant.application.entity;

import career.assistant.job.entity.Job;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "applications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_applications_job",
                        columnNames = {"job_id"}
                )
        }
)
public class Application {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "job_id",
            nullable = false,
            unique = true
    )
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 50
    )
    private ApplicationStatus status = ApplicationStatus.PENDING_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "application_type",
            nullable = false,
            length = 50
    )
    private ApplicationType applicationType = ApplicationType.MANUAL;

    @Column(
            name = "application_url",
            length = 1000
    )
    private String applicationUrl;

    @Column(name = "resume_version_id")
    private UUID resumeVersionId;

    @Column(name = "cover_letter_id")
    private UUID coverLetterId;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(
            name = "error_message",
            columnDefinition = "TEXT"
    )
    private String errorMessage;

    @Column(
            columnDefinition = "TEXT"
    )
    private String notes;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "adapter", length = 30)
    private String adapter;

    @Column(name = "confirmation_id", length = 500)
    private String confirmationId;

    @Column(name = "confirmation_url", length = 1000)
    private String confirmationUrl;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "submission_attempted_at")
    private OffsetDateTime submissionAttemptedAt;

    @Column(name = "notification_sent_at")
    private OffsetDateTime notificationSentAt;

    @Column(
            name = "created_at",
            nullable = false
    )
    private OffsetDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {

        OffsetDateTime now = OffsetDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
        if (idempotencyKey == null && job != null && job.getId() != null) {
            idempotencyKey = "job:" + job.getId();
        }
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public ApplicationType getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(ApplicationType applicationType) {
        this.applicationType = applicationType;
    }

    public String getApplicationUrl() {
        return applicationUrl;
    }

    public void setApplicationUrl(String applicationUrl) {
        this.applicationUrl = applicationUrl;
    }

    public UUID getResumeVersionId() {
        return resumeVersionId;
    }

    public void setResumeVersionId(UUID resumeVersionId) {
        this.resumeVersionId = resumeVersionId;
    }

    public UUID getCoverLetterId() {
        return coverLetterId;
    }

    public void setCoverLetterId(UUID coverLetterId) {
        this.coverLetterId = coverLetterId;
    }

    public OffsetDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(OffsetDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public String getAdapter(){return adapter;} public void setAdapter(String v){adapter=v;}
    public String getConfirmationId(){return confirmationId;} public void setConfirmationId(String v){confirmationId=v;}
    public String getConfirmationUrl(){return confirmationUrl;} public void setConfirmationUrl(String v){confirmationUrl=v;}
    public OffsetDateTime getSubmittedAt(){return submittedAt;} public void setSubmittedAt(OffsetDateTime v){submittedAt=v;}
    public OffsetDateTime getSubmissionAttemptedAt(){return submissionAttemptedAt;} public void setSubmissionAttemptedAt(OffsetDateTime v){submissionAttemptedAt=v;}
    public OffsetDateTime getNotificationSentAt(){return notificationSentAt;} public void setNotificationSentAt(OffsetDateTime v){notificationSentAt=v;}
}
