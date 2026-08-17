package career.assistant.document.storage;

public record StoredResumeObject(byte[] content, String contentType) {
    public StoredResumeObject {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
