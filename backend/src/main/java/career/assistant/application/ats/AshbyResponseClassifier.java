package career.assistant.application.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

@Component
public class AshbyResponseClassifier {
    private final ObjectMapper objectMapper;

    @Autowired
    public AshbyResponseClassifier() {
        this(new ObjectMapper());
    }

    public AshbyResponseClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AtsAdapter.AdapterResult classify(int statusCode, String responseBody) {
        String lower = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
        if (lower.contains("captcha") || lower.contains("challenge"))
            return AtsAdapter.AdapterResult.pending(AtsAdapter.Outcome.CAPTCHA, "Ashby returned a CAPTCHA or anti-automation challenge; manual review is required.");
        if (lower.contains("multi-factor") || lower.contains("mfa") || lower.contains("one-time code"))
            return AtsAdapter.AdapterResult.pending(AtsAdapter.Outcome.MFA, "Ashby returned an MFA requirement; manual review is required.");
        if (lower.contains("required") && (lower.contains("missing") || lower.contains("error")))
            return AtsAdapter.AdapterResult.pending(AtsAdapter.Outcome.MISSING_ANSWERS, "Ashby reported missing mandatory answers; no retry will be attempted.");
        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
            if (root.path("blocked").asBoolean(false))
                return AtsAdapter.AdapterResult.pending(AtsAdapter.Outcome.REJECTED, "Ashby positively reported that the application was blocked.");
            String confirmation = text(root.path("submittedFormInstance"), "id");
            if (confirmation == null) confirmation = text(root, "submittedFormInstanceId");
            if (confirmation == null) confirmation = text(root, "applicationId");
            String url = text(root, "confirmationUrl");
            if (statusCode >= 200 && statusCode < 300 && confirmation != null)
                return AtsAdapter.AdapterResult.confirmed(confirmation, url);
        } catch (Exception ignored) {
            // An unparseable or incomplete response is uncertain and must never be retried automatically.
        }
        if (statusCode >= 400 && statusCode < 500)
            return AtsAdapter.AdapterResult.pending(AtsAdapter.Outcome.REJECTED, "Ashby returned a non-success response; manual review is required.");
        return AtsAdapter.AdapterResult.pending(AtsAdapter.Outcome.UNCERTAIN, "Ashby response did not positively confirm submission; no automatic retry is allowed.");
    }

    public AtsAdapter.AdapterResult uncertainFailure() {
        return AtsAdapter.AdapterResult.pending(AtsAdapter.Outcome.UNCERTAIN, "Ashby transport outcome is uncertain; no automatic retry is allowed.");
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        if (value == null || !value.isValueNode() || value.asText().isBlank()) return null;
        return value.asText();
    }
}
