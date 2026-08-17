package career.assistant.document.validation;

public record ValidatedResumeFile(
        String sanitizedFilename,
        String contentType,
        String extension,
        byte[] content,
        String checksum
) {
    public ValidatedResumeFile {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
