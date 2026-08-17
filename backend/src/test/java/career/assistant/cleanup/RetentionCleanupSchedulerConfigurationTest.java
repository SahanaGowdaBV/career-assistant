package career.assistant.cleanup;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetentionCleanupSchedulerConfigurationTest {

    @Test
    void scheduledCleanupUsesConfiguredCron() throws NoSuchMethodException {
        Scheduled scheduled = scheduledAnnotation();
        assertEquals("${career.cleanup.cron:0 30 2 * * *}", scheduled.cron());
    }

    @Test
    void scheduledCleanupUsesConfiguredTimezone() throws NoSuchMethodException {
        Scheduled scheduled = scheduledAnnotation();
        assertEquals("${career.cleanup.zone:Asia/Kolkata}", scheduled.zone());
    }

    private Scheduled scheduledAnnotation() throws NoSuchMethodException {
        Scheduled scheduled = RetentionCleanupService.class
                .getDeclaredMethod("scheduled")
                .getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        return scheduled;
    }
}
