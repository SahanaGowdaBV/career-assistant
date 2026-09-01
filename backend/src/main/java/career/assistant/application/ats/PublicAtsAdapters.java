package career.assistant.application.ats;

import career.assistant.application.entity.Application;
import career.assistant.job.entity.Job;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class PublicAtsAdapters {
    private final List<AtsAdapter> adapters;

    public PublicAtsAdapters(AshbyPublicFormDiscovery ashbyDiscovery, AshbyResponseClassifier responseClassifier) {
        this.adapters = List.of(
                adapter("GREENHOUSE", "boards.greenhouse.io", Pattern.compile("^/[^/]+/(?:jobs/)?\\d+/?$")),
                adapter("LEVER", "jobs.lever.co", Pattern.compile("^/[^/]+/[0-9a-fA-F-]{16,}/?$")),
                adapter("WORKABLE", "apply.workable.com", Pattern.compile("^/[^/]+/j/[A-Za-z0-9_-]+/?$")),
                ashby(ashbyDiscovery, responseClassifier)
        );
    }

    public Optional<AtsAdapter> resolve(String url) { return adapters.stream().filter(adapter -> adapter.supports(url)).findFirst(); }
    public List<String> supported() { return adapters.stream().map(AtsAdapter::name).toList(); }

    private static AtsAdapter adapter(String name, String host, Pattern path) {
        return new AtsAdapter() {
            public String name() { return name; }
            public boolean supports(String url) {
                try {
                    URI uri = URI.create(url);
                    return "https".equalsIgnoreCase(uri.getScheme()) && host.equalsIgnoreCase(uri.getHost()) && path.matcher(uri.getPath()).matches();
                } catch (Exception exception) { return false; }
            }
            public AdapterResult dryRun(Job job, Application application, PreparedApplication prepared) { return AdapterResult.dry(name); }
        };
    }

    private static AtsAdapter ashby(AshbyPublicFormDiscovery discovery, AshbyResponseClassifier responseClassifier) {
        return new AtsAdapter() {
            public String name() { return "ASHBY"; }
            public boolean supports(String url) { return parseAshbyUrl(url).isPresent(); }
            public List<FormField> requiredFields(Job job) {
                return List.of(
                        new FormField("_systemfield_name", "Name", "String", true, List.of()),
                        new FormField("_systemfield_email", "Email", "Email", true, List.of()),
                        new FormField("_systemfield_resume", "Resume", "File", true, List.of()),
                        new FormField("_systemfield_phone", "Phone", "Phone", true, List.of())
                );
            }
            public FormDefinition discover(Job job) { return discovery.discover(job.getJobUrl()); }
            public AdapterResult dryRun(Job job, Application application, PreparedApplication prepared) {
                return AdapterResult.dry(name());
            }
            public AdapterResult classifyResponse(int statusCode, String responseBody) {
                return responseClassifier.classify(statusCode, responseBody);
            }
        };
    }

    public static Optional<AshbyPosting> parseAshbyUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"jobs.ashbyhq.com".equalsIgnoreCase(uri.getHost())) return Optional.empty();
            String[] parts = uri.getPath().split("/");
            if (parts.length != 3 || !parts[1].matches("[A-Za-z0-9_-]+")
                    || !parts[2].matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) return Optional.empty();
            return Optional.of(new AshbyPosting(parts[1], UUID.fromString(parts[2])));
        } catch (Exception exception) { return Optional.empty(); }
    }

    public record AshbyPosting(String boardSlug, UUID postingId) { }

    public static String unsupportedReason(String url) {
        String host = "unknown";
        try { host = URI.create(url).getHost(); } catch (Exception ignored) { }
        if (host == null) host = "unknown";
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.contains("workday")) return "Workday requires portal-specific review";
        if (normalized.contains("oracle") || normalized.contains("taleo")) return "Oracle/Taleo requires portal-specific review";
        if (normalized.contains("linkedin") || normalized.contains("naukri")) return "Authenticated job portal requires manual review";
        return "Unsupported or non-public application form (authentication/CAPTCHA is never bypassed): " + host;
    }
}
