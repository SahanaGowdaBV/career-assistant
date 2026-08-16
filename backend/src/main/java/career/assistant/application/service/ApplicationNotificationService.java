package career.assistant.application.service;

import career.assistant.application.entity.Application;
import career.assistant.application.entity.ApplicationStatus;
import career.assistant.application.repository.ApplicationRepository;
import career.assistant.company.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
public class ApplicationNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationNotificationService.class);
    private static final Set<ApplicationStatus> SUCCESSFUL_STATUSES = Set.of(
            ApplicationStatus.AUTO_APPLIED,
            ApplicationStatus.MANUALLY_APPLIED
    );

    private final ApplicationRepository applications;
    private final CompanyRepository companies;
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String username;
    private final String notificationRecipient;

    public ApplicationNotificationService(
            ApplicationRepository applications,
            CompanyRepository companies,
            JavaMailSender sender,
            @Value("${career.mail.enabled:false}") boolean enabled,
            @Value("${career.mail.username:}") String username,
            @Value("${career.mail.notification-to:sahana.gowda2227@gmail.com}") String notificationRecipient
    ) {
        this.applications = applications;
        this.companies = companies;
        this.sender = sender;
        this.enabled = enabled;
        this.username = username;
        this.notificationRecipient = notificationRecipient;
    }

    @Transactional
    public boolean sendVerifiedSuccessOnce(Application application, String score) {
        if (!enabled
                || !SUCCESSFUL_STATUSES.contains(application.getStatus())
                || application.getNotificationSentAt() != null) {
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        if (!username.isBlank()) {
            message.setFrom(username);
        }
        message.setTo(notificationRecipient);
        String company = companies.findById(application.getJob().getCompanyId())
                .map(value -> value.getName())
                .orElse("Unknown company");
        message.setSubject("Application submitted: " + application.getJob().getTitle() + " at " + company);
        message.setText(
                "Job: " + application.getJob().getTitle()
                        + "\nCompany: " + company
                        + "\nScore: " + score
                        + "\nTimestamp: " + application.getSubmittedAt()
                        + "\nSource: " + application.getApplicationUrl()
                        + "\nConfirmation: " + application.getConfirmationId()
                        + "\nResume version: " + application.getResumeVersionId()
        );

        try {
            sender.send(message);
        } catch (MailException exception) {
            LOGGER.warn(
                    "Application notification delivery failed for status {}; notification remains retryable",
                    application.getStatus()
            );
            return false;
        }

        application.setNotificationSentAt(OffsetDateTime.now());
        applications.save(application);
        return true;
    }
}
