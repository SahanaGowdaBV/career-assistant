package career.assistant.scraper.controller;

import career.assistant.company.service.CompanyService;
import career.assistant.job.dto.JobResponse;
import career.assistant.job.mapper.JobMapper;
import career.assistant.job.service.JobService;
import career.assistant.scraper.config.JobSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/scraper")
public class IngestionController {

    private static final List<String> UAE_MARKERS = List.of(
            "dubai", "abu dhabi", "sharjah", "uae", "united arab emirates"
    );
    private static final List<String> EXCLUDED_LOCATION_MARKERS = List.of(
            "india", "bengaluru", "bangalore", "hyderabad", "pune", "chennai", "mumbai",
            "noida", "gurugram", "gurgaon", "delhi", "saudi arabia", "riyadh", "jeddah",
            "egypt", "cairo", "europe", "united states", "usa", "u.s.a"
    );

    private final JobService jobs;
    private final CompanyService companies;

    public IngestionController(JobService jobs, CompanyService companies) {
        this.jobs = jobs;
        this.companies = companies;
    }

    @PostMapping("/ingest")
    public IngestResult ingest(@Valid @RequestBody IngestRequest request) {
        int accepted = 0;
        int rejected = 0;
        int duplicates = 0;
        List<JobResponse> saved = new ArrayList<>();

        for (IngestJob raw : request.jobs()) {
            if (!eligible(raw)) {
                rejected++;
                continue;
            }
            String canonicalUrl = canonicalUrl(raw.url());
            if (jobs.existsBySourceAndSourceJobId(raw.source(), raw.sourceId())
                    || jobs.existsByCanonicalUrl(canonicalUrl)
                    || jobs.existsByJobUrlIn(urlVariants(canonicalUrl))) {
                duplicates++;
                continue;
            }
            if (request.dryRun()) {
                accepted++;
                continue;
            }

            var company = companies.findOrCreate(raw.company());
            var job = new career.assistant.job.entity.Job();
            job.setTitle(raw.title());
            job.setCompanyId(company.getId());
            job.setDescription(raw.description());
            job.setLocation(raw.location());
            job.setCountry("United Arab Emirates");
            job.setCity(city(raw.location()));
            job.setExperienceMin(raw.experienceMin());
            job.setExperienceMax(raw.experienceMax());
            job.setSource(raw.source());
            job.setSourceJobId(raw.sourceId());
            job.setJobUrl(canonicalUrl);
            job.setCanonicalUrl(canonicalUrl);
            job.setPostedAt(raw.postedAt());
            job.setStatus(raw.experienceUnknown() ? "PENDING_REVIEW" : "NEW");
            saved.add(JobMapper.toResponse(jobs.create(job)));
            accepted++;
        }
        return new IngestResult(request.dryRun(), accepted, rejected, duplicates, saved);
    }

    private boolean eligible(IngestJob job) {
        String location = job.location().toLowerCase(Locale.ROOT);
        boolean excluded = EXCLUDED_LOCATION_MARKERS.stream().anyMatch(location::contains);
        boolean uae = UAE_MARKERS.stream().anyMatch(location::contains);
        boolean experience = job.experienceUnknown()
                ? job.experienceMin() == null && job.experienceMax() == null
                : (job.experienceMin() == null || job.experienceMin() <= 8)
                    && (job.experienceMax() == null || job.experienceMax() >= 4);
        return uae && !excluded && experience;
    }

    private String city(String location) {
        String normalized = location.toLowerCase(Locale.ROOT);
        if (normalized.contains("abu dhabi")) return "Abu Dhabi";
        if (normalized.contains("sharjah")) return "Sharjah";
        if (normalized.contains("dubai")) return "Dubai";
        return "Remote";
    }

    private String canonicalUrl(String value) {
        URI uri = URI.create(value.trim()).normalize();
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
            port = -1;
        }
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            path = "";
        } else if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return URI.create(scheme + "://" + host + (port < 0 ? "" : ":" + port) + path
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())).toString();
    }

    private List<String> urlVariants(String canonicalUrl) {
        int queryIndex = canonicalUrl.indexOf('?');
        String base = queryIndex < 0 ? canonicalUrl : canonicalUrl.substring(0, queryIndex);
        String query = queryIndex < 0 ? "" : canonicalUrl.substring(queryIndex);
        String alternate = base.endsWith("/")
                ? base.substring(0, base.length() - 1) + query
                : base + "/" + query;
        return List.of(canonicalUrl, alternate);
    }

    public record IngestRequest(boolean dryRun, @NotEmpty List<@Valid IngestJob> jobs) {}

    public record IngestJob(
            @NotBlank String title,
            @NotBlank String company,
            @NotBlank String location,
            Integer experienceMin,
            Integer experienceMax,
            boolean experienceUnknown,
            @NotNull JobSource source,
            @NotBlank String sourceId,
            @NotBlank String url,
            String description,
            OffsetDateTime postedAt
    ) {}

    public record IngestResult(
            boolean dryRun,
            int accepted,
            int rejected,
            int duplicates,
            List<JobResponse> jobs
    ) {}
}
