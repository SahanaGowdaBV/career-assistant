package career.assistant.document.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "career.resume.storage", havingValue = "supabase")
public class SupabaseResumeStorage implements ResumeStorage {

    private static final Pattern SAFE_BUCKET = Pattern.compile("[a-z0-9][a-z0-9_-]{1,62}");

    private final RestClient client;
    private final String bucket;

    public SupabaseResumeStorage(
            RestClient.Builder builder,
            @Value("${career.resume.supabase.url}") String supabaseUrl,
            @Value("${career.resume.supabase.service-role-key}") String serviceRoleKey,
            @Value("${career.resume.supabase.bucket:resumes}") String bucket
    ) {
        if (supabaseUrl == null || supabaseUrl.isBlank() || serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IllegalStateException("Supabase resume storage is enabled but backend credentials are not configured");
        }
        if (!SAFE_BUCKET.matcher(bucket).matches()) {
            throw new IllegalStateException("Supabase resume bucket name is invalid");
        }
        this.bucket = bucket;
        this.client = builder
                .baseUrl(supabaseUrl.replaceAll("/+$", "") + "/storage/v1")
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
                .build();
    }

    @PostConstruct
    void verifyPrivateBucket() {
        try {
            BucketDetails details = client.get()
                    .uri("/bucket/{bucket}", bucket)
                    .retrieve()
                    .body(BucketDetails.class);
            if (details == null || details.isPublic()) {
                throw new IllegalStateException("The configured Supabase resume bucket must exist and be private");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to verify the private Supabase resume bucket", exception);
        }
    }

    @Override
    public void store(String objectPath, byte[] content, String contentType) {
        InMemoryResumeStorage.requireSafeObjectPath(objectPath);
        try {
            client.post()
                    .uri(uriBuilder -> uriBuilder.path("/object/{bucket}/").path(objectPath).build(bucket))
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("x-upsert", "false")
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new ResumeStorageException("Unable to store resume file", exception);
        }
    }

    @Override
    public StoredResumeObject load(String objectPath) {
        InMemoryResumeStorage.requireSafeObjectPath(objectPath);
        try {
            byte[] content = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/object/{bucket}/").path(objectPath).build(bucket))
                    .retrieve()
                    .body(byte[].class);
            if (content == null) {
                throw new ResumeStorageException("Stored resume file was empty");
            }
            return new StoredResumeObject(content, null);
        } catch (ResumeStorageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResumeStorageException("Unable to load resume file", exception);
        }
    }

    @Override
    public void delete(String objectPath) {
        InMemoryResumeStorage.requireSafeObjectPath(objectPath);
        try {
            client.delete()
                    .uri(uriBuilder -> uriBuilder.path("/object/{bucket}/").path(objectPath).build(bucket))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new ResumeStorageException("Unable to delete resume file", exception);
        }
    }

    private record BucketDetails(@JsonProperty("public") boolean isPublic) {
    }
}
