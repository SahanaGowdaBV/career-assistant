package career.assistant.application.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AshbyResponseClassifierTest {
    private final AshbyResponseClassifier classifier = new AshbyResponseClassifier(new ObjectMapper());

    @Test void capturesOnlyPositiveConfirmation() {
        var result = classifier.classify(200, "{\"submittedFormInstance\":{\"id\":\"confirmation-123\"},\"confirmationUrl\":\"https://jobs.ashbyhq.com/confirmation\"}");
        assertEquals(AtsAdapter.Outcome.CONFIRMED, result.outcome());
        assertTrue(result.verified());
        assertEquals("confirmation-123", result.confirmationId());
    }

    @Test void classifiesChallengesMissingAnswersAndAmbiguousSuccessForReviewWithoutRetry() {
        assertEquals(AtsAdapter.Outcome.CAPTCHA, classifier.classify(403, "captcha challenge").outcome());
        assertEquals(AtsAdapter.Outcome.MFA, classifier.classify(401, "MFA one-time code").outcome());
        assertEquals(AtsAdapter.Outcome.MISSING_ANSWERS, classifier.classify(400, "required field missing error").outcome());
        var uncertain = classifier.classify(200, "{\"success\":true}");
        assertEquals(AtsAdapter.Outcome.UNCERTAIN, uncertain.outcome());
        assertFalse(uncertain.verified());
        assertNull(uncertain.confirmationId());
        assertTrue(uncertain.reason().contains("no automatic retry"));
    }
}
