package career.assistant.cleanup;

import career.assistant.application.entity.Application;
import career.assistant.application.entity.ApplicationStatus;
import career.assistant.application.repository.ApplicationRepository;
import career.assistant.document.entity.CoverLetter;
import career.assistant.document.entity.ResumeVersion;
import career.assistant.document.repository.CoverLetterRepository;
import career.assistant.document.repository.ResumeVersionRepository;
import career.assistant.document.storage.ResumeStorage;
import career.assistant.job.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RetentionCleanupService {

    private static final Set<ApplicationStatus> PROTECTED_APPLICATION_STATUSES = Set.of(
            ApplicationStatus.PENDING_REVIEW,
            ApplicationStatus.READY_TO_APPLY,
            ApplicationStatus.NEW,
            ApplicationStatus.HIGH_SCORE,
            ApplicationStatus.AUTO_APPLIED,
            ApplicationStatus.MANUALLY_APPLIED
    );

    private static final Set<ApplicationStatus> SUCCESSFUL_APPLICATION_STATUSES = Set.of(
            ApplicationStatus.AUTO_APPLIED,
            ApplicationStatus.MANUALLY_APPLIED
    );

    private final ResumeVersionRepository resumes;
    private final CoverLetterRepository letters;
    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final CleanupAuditRepository audits;
    private final ResumeStorage storage;
    private final boolean scheduledCleanupEnabled;
    private final boolean dryRun;
    private final int unusedDocumentDays;
    private final int rejectedJobDays;
    private final int failedApplicationDays;
    private final int successfulApplicationDays;

    public RetentionCleanupService(
            ResumeVersionRepository resumes,
            CoverLetterRepository letters,
            ApplicationRepository applications,
            JobRepository jobs,
            CleanupAuditRepository audits,
            ResumeStorage storage,
            @Value("${career.cleanup.enabled:false}") boolean scheduledCleanupEnabled,
            @Value("${career.cleanup.dry-run:true}") boolean dryRun,
            @Value("${career.cleanup.unused-documents-days:30}") int unusedDocumentDays,
            @Value("${career.cleanup.rejected-jobs-days:90}") int rejectedJobDays,
            @Value("${career.cleanup.failed-attempts-days:90}") int failedApplicationDays,
            @Value("${career.cleanup.successful-applications-days:365}") int successfulApplicationDays
    ) {
        this.resumes = resumes;
        this.letters = letters;
        this.applications = applications;
        this.jobs = jobs;
        this.audits = audits;
        this.storage = storage;
        this.scheduledCleanupEnabled = scheduledCleanupEnabled;
        this.dryRun = dryRun;
        this.unusedDocumentDays = unusedDocumentDays;
        this.rejectedJobDays = rejectedJobDays;
        this.failedApplicationDays = failedApplicationDays;
        this.successfulApplicationDays = successfulApplicationDays;
    }

    @Scheduled(
            cron = "${career.cleanup.cron:0 30 2 * * *}",
            zone = "${career.cleanup.zone:Asia/Kolkata}"
    )
    @Transactional
    public void scheduled() {
        if (scheduledCleanupEnabled) {
            execute(dryRun);
        }
    }

    @Transactional
    public CleanupResult run() {
        return execute(dryRun);
    }

    @Transactional
    public CleanupResult preview() {
        return execute(true);
    }

    private CleanupResult execute(boolean preview) {
        Map<String, Integer> categories = emptyCategories();
        int deleted = 0;
        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime earliestApplicationCutoff = now.minusDays(
                Math.min(failedApplicationDays, successfulApplicationDays)
        );
        for (Application application : applications.findByUpdatedAtBefore(earliestApplicationCutoff)) {
            if (!expiredApplication(application, now)) {
                continue;
            }
            record(categories, preview, "APPLICATION", application.getId(), null,
                    "Expired failed or successful application");
            if (!preview) {
                applications.delete(application);
                deleted++;
            }
        }

        for (var job : jobs.findByStatusInAndUpdatedAtBefore(
                List.of("REJECTED", "ARCHIVED"), now.minusDays(rejectedJobDays))) {
            if (applications.existsByJobId(job.getId())) {
                continue;
            }
            record(categories, preview, "JOB", job.getId(), null,
                    "Expired rejected or archived unreferenced job");
            if (!preview) {
                jobs.delete(job);
                deleted++;
            }
        }

        OffsetDateTime unusedDocumentCutoff = now.minusDays(unusedDocumentDays);
        for (ResumeVersion resume : resumes.findByCreatedAtBefore(unusedDocumentCutoff)) {
            if (!resume.isCustomized()
                    || resume.isMasterResume()
                    || applications.existsByResumeVersionIdAndStatusIn(
                            resume.getId(), PROTECTED_APPLICATION_STATUSES)) {
                continue;
            }
            record(categories, preview, "RESUME", resume.getId(), resume.getStoragePath(),
                    "Expired generated resume without a protected application reference");
            if (!preview) {
                deleteStorageObject(resume.getStoragePath());
                resumes.delete(resume);
                deleted++;
            }
        }

        for (CoverLetter letter : letters.findByCreatedAtBefore(unusedDocumentCutoff)) {
            if (!letter.isCustomized()
                    || applications.existsByCoverLetterIdAndStatusIn(
                            letter.getId(), PROTECTED_APPLICATION_STATUSES)) {
                continue;
            }
            record(categories, preview, "COVER_LETTER", letter.getId(), letter.getStoragePath(),
                    "Expired generated cover letter without a protected application reference");
            if (!preview) {
                deleteStorageObject(letter.getStoragePath());
                letters.delete(letter);
                deleted++;
            }
        }

        int candidates = categories.values().stream().mapToInt(Integer::intValue).sum();
        return new CleanupResult(preview, candidates, deleted, Map.copyOf(categories));
    }

    private boolean expiredApplication(Application application, OffsetDateTime now) {
        if (application.getUpdatedAt() == null) {
            return false;
        }
        if (application.getStatus() == ApplicationStatus.FAILED) {
            return application.getUpdatedAt().isBefore(now.minusDays(failedApplicationDays));
        }
        return SUCCESSFUL_APPLICATION_STATUSES.contains(application.getStatus())
                && application.getUpdatedAt().isBefore(now.minusDays(successfulApplicationDays));
    }

    private void deleteStorageObject(String storagePath) {
        if (storagePath != null && !storagePath.isBlank()) {
            storage.delete(storagePath);
        }
    }

    private void record(
            Map<String, Integer> categories,
            boolean preview,
            String category,
            UUID entityId,
            String storagePath,
            String details
    ) {
        categories.compute(category, (key, count) -> count + 1);
        CleanupAuditEntry entry = new CleanupAuditEntry();
        entry.setDryRun(preview);
        entry.setCategory(category);
        entry.setEntityId(entityId);
        entry.setStoragePath(storagePath);
        entry.setAction(preview ? "WOULD_DELETE" : "DELETE");
        entry.setDetails(details);
        audits.save(entry);
    }

    private static Map<String, Integer> emptyCategories() {
        Map<String, Integer> categories = new LinkedHashMap<>();
        categories.put("APPLICATION", 0);
        categories.put("JOB", 0);
        categories.put("RESUME", 0);
        categories.put("COVER_LETTER", 0);
        return categories;
    }

    public record CleanupResult(
            boolean dryRun,
            int candidates,
            int deleted,
            Map<String, Integer> categories
    ) {
    }
}
