package career.assistant.document.quality;

import career.assistant.document.service.ResumeConflictException;

public class DocumentQualityException extends ResumeConflictException {
    public DocumentQualityException(String message) {
        super(message);
    }
}
