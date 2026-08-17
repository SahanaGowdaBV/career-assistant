package career.assistant.document.storage;

public interface ResumeStorage {
    void store(String objectPath, byte[] content, String contentType);
    StoredResumeObject load(String objectPath);
    void delete(String objectPath);
}
