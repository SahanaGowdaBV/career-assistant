package career.assistant.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "resume_versions")
public class ResumeVersion {

    @Id
    @GeneratedValue
    private UUID id;
    @Column(name = "owner_subject", length = 255)
    private String ownerSubject;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "version_name", nullable = false)
    private String versionName;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(length = 64)
    private String checksum;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "master_resume", nullable = false)
    private boolean masterResume;

    @Column(name = "original_resume", nullable = false)
    private boolean originalResume;

    @Column(nullable = false)
    private boolean customized;

    @Column(name = "parsed_text", columnDefinition = "TEXT")
    private String parsedText;

    @Column(name = "structured_skills", columnDefinition = "TEXT")
    private String structuredSkills;

    @Column(name = "structured_experience", columnDefinition = "TEXT")
    private String structuredExperience;

    @Column(name = "source_resume_id")
    private UUID sourceResumeId;

    @Column(name = "customization_manifest", columnDefinition = "TEXT")
    private String customizationManifest;

    @Column(name = "customization_summary", columnDefinition = "TEXT")
    private String customizationSummary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void create() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public String getOwnerSubject() { return ownerSubject; }
    public void setOwnerSubject(String ownerSubject) { this.ownerSubject = ownerSubject; }
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public boolean isMasterResume() { return masterResume; }
    public void setMasterResume(boolean masterResume) { this.masterResume = masterResume; }
    public boolean isOriginalResume() { return originalResume; }
    public void setOriginalResume(boolean originalResume) { this.originalResume = originalResume; }
    public boolean isCustomized() { return customized; }
    public void setCustomized(boolean customized) { this.customized = customized; }
    public String getParsedText() { return parsedText; }
    public void setParsedText(String parsedText) { this.parsedText = parsedText; }
    public String getStructuredSkills() { return structuredSkills; }
    public void setStructuredSkills(String structuredSkills) { this.structuredSkills = structuredSkills; }
    public String getStructuredExperience() { return structuredExperience; }
    public void setStructuredExperience(String structuredExperience) { this.structuredExperience = structuredExperience; }
    public UUID getSourceResumeId() { return sourceResumeId; }
    public void setSourceResumeId(UUID sourceResumeId) { this.sourceResumeId = sourceResumeId; }
    public String getCustomizationManifest() { return customizationManifest; }
    public void setCustomizationManifest(String customizationManifest) { this.customizationManifest = customizationManifest; }
    public String getCustomizationSummary() { return customizationSummary; }
    public void setCustomizationSummary(String customizationSummary) { this.customizationSummary = customizationSummary; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
