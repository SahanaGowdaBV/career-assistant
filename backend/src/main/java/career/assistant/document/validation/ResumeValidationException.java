package career.assistant.document.validation;

public class ResumeValidationException extends RuntimeException {
    public ResumeValidationException(String message) {
        super(message);
    }

    public ResumeValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
