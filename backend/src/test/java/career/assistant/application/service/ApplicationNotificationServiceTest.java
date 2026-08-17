package career.assistant.application.service;

import career.assistant.application.entity.Application;
import career.assistant.application.entity.ApplicationStatus;
import career.assistant.application.repository.ApplicationRepository;
import career.assistant.company.repository.CompanyRepository;
import career.assistant.job.entity.Job;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationNotificationServiceTest {

    private static final String CONFIGURED_RECIPIENT = "configured-recipient@example.com";

    private ApplicationRepository applications;
    private CompanyRepository companies;
    private FakeMailSender sender;
    private ApplicationNotificationService service;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        companies = mock(CompanyRepository.class);
        sender = new FakeMailSender();
        service = new ApplicationNotificationService(
                applications,
                companies,
                sender,
                true,
                "sender@example.com",
                CONFIGURED_RECIPIENT
        );
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void onlyAppliedStatusesSendEmailToConfiguredRecipient() {
        for (ApplicationStatus status : List.of(
                ApplicationStatus.AUTO_APPLIED,
                ApplicationStatus.MANUALLY_APPLIED)) {
            assertTrue(service.sendVerifiedSuccessOnce(application(status), "95"));
        }

        assertEquals(2, sender.messages.size());
        assertTrue(sender.messages.stream().allMatch(message ->
                List.of(CONFIGURED_RECIPIENT).equals(List.of(message.getTo()))));
    }

    @Test
    void pendingReadyDryRunAndFailedStatesDoNotSendEmail() {
        EnumSet<ApplicationStatus> nonSuccessful = EnumSet.allOf(ApplicationStatus.class);
        nonSuccessful.remove(ApplicationStatus.AUTO_APPLIED);
        nonSuccessful.remove(ApplicationStatus.MANUALLY_APPLIED);

        for (ApplicationStatus status : nonSuccessful) {
            assertFalse(service.sendVerifiedSuccessOnce(application(status), "95"));
        }

        assertTrue(nonSuccessful.contains(ApplicationStatus.PENDING_REVIEW));
        assertTrue(nonSuccessful.contains(ApplicationStatus.READY_TO_APPLY));
        assertTrue(nonSuccessful.contains(ApplicationStatus.FAILED));
        assertTrue(sender.messages.isEmpty());
        verify(applications, never()).save(any());
    }

    @Test
    void repeatingSuccessfulTransitionDoesNotSendDuplicate() {
        Application application = application(ApplicationStatus.AUTO_APPLIED);

        assertTrue(service.sendVerifiedSuccessOnce(application, "95"));
        assertFalse(service.sendVerifiedSuccessOnce(application, "95"));

        assertEquals(1, sender.messages.size());
    }

    @Test
    void smtpFailurePreservesStatusIsLoggedAndCanBeRetried() {
        Application application = application(ApplicationStatus.AUTO_APPLIED);
        sender.fail = true;
        Logger logger = (Logger) LoggerFactory.getLogger(ApplicationNotificationService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertFalse(service.sendVerifiedSuccessOnce(application, "95"));
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }

        assertEquals(ApplicationStatus.AUTO_APPLIED, application.getStatus());
        assertNull(application.getNotificationSentAt());
        verify(applications, never()).save(any());
        assertEquals(1, logs.list.size());
        assertEquals(Level.WARN, logs.list.getFirst().getLevel());
        assertFalse(logs.list.getFirst().getFormattedMessage().contains(CONFIGURED_RECIPIENT));

        sender.fail = false;
        assertTrue(service.sendVerifiedSuccessOnce(application, "95"));
        assertEquals(1, sender.messages.size());
        verify(applications).save(application);
    }

    @Test
    void disabledMailNeverSendsEvenForSuccessfulStatus() {
        service = new ApplicationNotificationService(
                applications,
                companies,
                sender,
                false,
                "sender@example.com",
                CONFIGURED_RECIPIENT
        );

        assertFalse(service.sendVerifiedSuccessOnce(application(ApplicationStatus.AUTO_APPLIED), "95"));
        assertTrue(sender.messages.isEmpty());
    }

    @Test
    void enabledMailRequiresConfiguredNotificationRecipient() {
        assertThrows(IllegalStateException.class, () -> new ApplicationNotificationService(
                applications,
                companies,
                sender,
                true,
                "sender@example.com",
                "  "
        ));
    }

    private Application application(ApplicationStatus status) {
        Job job = mock(Job.class);
        when(job.getTitle()).thenReturn("Engineer");
        when(job.getCompanyId()).thenReturn(UUID.randomUUID());
        Application application = new Application();
        application.setJob(job);
        application.setStatus(status);
        application.setConfirmationId("CONF-1");
        application.setSubmittedAt(OffsetDateTime.now());
        application.setResumeVersionId(UUID.randomUUID());
        return application;
    }

    private static final class FakeMailSender extends JavaMailSenderImpl {

        private final List<SimpleMailMessage> messages = new ArrayList<>();
        private boolean fail;

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            send(new SimpleMailMessage[]{simpleMessage});
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            if (fail) {
                throw new MailSendException("Simulated SMTP failure");
            }
            for (SimpleMailMessage message : simpleMessages) {
                messages.add(new SimpleMailMessage(message));
            }
        }
    }
}
