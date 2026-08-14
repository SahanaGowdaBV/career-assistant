package career.assistant.document.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "career.resume.storage", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryResumeStorage implements ResumeStorage {

    private final Map<String, StoredResumeObject> objects = new ConcurrentHashMap<>();

    @Override
    public void store(String objectPath, byte[] content, String contentType) {
        requireSafeObjectPath(objectPath);
        objects.put(objectPath, new StoredResumeObject(content, contentType));
    }

    @Override
    public StoredResumeObject load(String objectPath) {
        requireSafeObjectPath(objectPath);
        StoredResumeObject object = objects.get(objectPath);
        if (object == null) {
            throw new ResumeStorageException("Stored resume file was not found");
        }
        return object;
    }

    @Override
    public void delete(String objectPath) {
        requireSafeObjectPath(objectPath);
        objects.remove(objectPath);
    }

    static void requireSafeObjectPath(String objectPath) {
        if (objectPath == null || objectPath.isBlank() || objectPath.startsWith("/")
                || objectPath.contains("..") || objectPath.contains("\\")) {
            throw new ResumeStorageException("Invalid resume storage path");
        }
    }
}
