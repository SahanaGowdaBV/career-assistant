package career.assistant.application.ats;

import career.assistant.application.entity.Application;
import career.assistant.job.entity.Job;

import java.util.List;
import java.util.Map;

public interface AtsAdapter {
    String name();
    boolean supports(String url);
    default List<FormField> requiredFields(Job job) { return List.of(); }
    default FormDefinition discover(Job job) { return new FormDefinition(null, requiredFields(job), false); }
    AdapterResult dryRun(Job job, Application application, PreparedApplication prepared);
    default AdapterResult classifyResponse(int statusCode, String responseBody) {
        return AdapterResult.pending(Outcome.UNCERTAIN, "ATS response did not positively confirm submission; no automatic retry is allowed.");
    }

    record FormField(String path, String name, String type, boolean required, List<String> options) {
        public FormField { options = options == null ? List.of() : List.copyOf(options); }
        public FormField(String name, boolean required) { this(name, name, "String", required, List.of()); }
    }
    record FormDefinition(String postingId, List<FormField> fields, boolean formTokenPresent) {
        public FormDefinition { fields = fields == null ? List.of() : List.copyOf(fields); }
    }
    record PreparedField(String path, String name, boolean present, String source) { }
    record PreparedUpload(String path, String documentType, String fileName, String contentType, long contentLength, String checksum) { }
    record PreparedApplication(List<PreparedField> fields, List<PreparedUpload> uploads, boolean formTokenPresent) {
        public PreparedApplication {
            fields = fields == null ? List.of() : List.copyOf(fields);
            uploads = uploads == null ? List.of() : List.copyOf(uploads);
        }
        public List<String> missingRequired(FormDefinition form) {
            Map<String, Boolean> present = fields.stream().collect(java.util.stream.Collectors.toMap(PreparedField::path, PreparedField::present, (a, b) -> a));
            return form.fields().stream().filter(FormField::required)
                    .filter(field -> !present.getOrDefault(field.path(), false)).map(FormField::name).toList();
        }
    }
    enum Outcome { DRY_RUN_READY, CONFIRMED, CAPTCHA, MFA, MISSING_ANSWERS, UNEXPECTED_QUESTIONS, UNCERTAIN, REJECTED }
    record AdapterResult(Outcome outcome, boolean verified, String confirmationId, String confirmationUrl, String reason) {
        public static AdapterResult dry(String adapter) { return new AdapterResult(Outcome.DRY_RUN_READY, false, null, null, "Dry run prepared a redacted " + adapter + " application preview; no request was submitted."); }
        public static AdapterResult pending(Outcome outcome, String reason) { return new AdapterResult(outcome, false, null, null, reason); }
        public static AdapterResult confirmed(String id, String url) { return new AdapterResult(Outcome.CONFIRMED, true, id, url, "External submission positively confirmed."); }
    }
}
