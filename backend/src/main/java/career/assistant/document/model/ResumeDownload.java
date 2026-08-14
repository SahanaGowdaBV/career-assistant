package career.assistant.document.model;

public record ResumeDownload(byte[] content, String contentType, String filename) {
    public ResumeDownload {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
