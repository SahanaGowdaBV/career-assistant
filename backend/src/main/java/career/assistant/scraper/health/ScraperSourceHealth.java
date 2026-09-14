package career.assistant.scraper.health;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "scraper_source_health")
public class ScraperSourceHealth {
    @Id
    @Column(name = "source_key", length = 255)
    private String sourceKey;
    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;
    @Column(name = "source_kind", nullable = false, length = 100)
    private String sourceKind;
    @Column(name = "source_status", nullable = false, length = 50)
    private String sourceStatus;
    @Column(name = "career_url", length = 1000)
    private String careerUrl;
    private int discovered;
    private int fetched;
    private int accepted;
    private int rejected;
    private int duplicates;
    @Column(name = "elapsed_ms", nullable = false)
    private long elapsedMs;
    @Column(name = "error_type", length = 255)
    private String errorType;
    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;
    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceKind() { return sourceKind; }
    public void setSourceKind(String sourceKind) { this.sourceKind = sourceKind; }
    public String getSourceStatus() { return sourceStatus; }
    public void setSourceStatus(String sourceStatus) { this.sourceStatus = sourceStatus; }
    public String getCareerUrl() { return careerUrl; }
    public void setCareerUrl(String careerUrl) { this.careerUrl = careerUrl; }
    public int getDiscovered() { return discovered; }
    public void setDiscovered(int discovered) { this.discovered = discovered; }
    public int getFetched() { return fetched; }
    public void setFetched(int fetched) { this.fetched = fetched; }
    public int getAccepted() { return accepted; }
    public void setAccepted(int accepted) { this.accepted = accepted; }
    public int getRejected() { return rejected; }
    public void setRejected(int rejected) { this.rejected = rejected; }
    public int getDuplicates() { return duplicates; }
    public void setDuplicates(int duplicates) { this.duplicates = duplicates; }
    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public OffsetDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(OffsetDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public OffsetDateTime getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(OffsetDateTime lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
