package career.assistant.job.service;

import career.assistant.company.service.CompanyService;
import career.assistant.job.dto.ManualJobRequest;
import career.assistant.job.entity.Job;
import career.assistant.job.exception.DuplicateJobException;
import career.assistant.job.repository.JobRepository;
import career.assistant.scraper.config.JobSource;
import career.assistant.security.AuthenticatedOwner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ManualJobImportService {
    private static final Pattern RANGE = Pattern.compile("(?i)\\b(\\d{1,2})\\s*(?:-|–|—|to)\\s*(\\d{1,2})\\s*(?:years?|yrs?)\\b");
    private static final Pattern MINIMUM = Pattern.compile("(?i)\\b(?:minimum(?: of)?|at least)?\\s*(\\d{1,2})\\+?\\s*(?:years?|yrs?)\\b");
    private final JobRepository jobs;
    private final CompanyService companies;

    public ManualJobImportService(JobRepository jobs, CompanyService companies) {
        this.jobs = jobs;
        this.companies = companies;
    }

    @Transactional
    public Job create(ManualJobRequest request) {
        String owner = AuthenticatedOwner.required();
        String canonicalUrl = canonicalPublicHttpsUrl(request.applicationUrl());
        if (jobs.existsByCanonicalUrlIgnoreCase(canonicalUrl) || jobs.existsByJobUrlIn(JobService.urlVariants(canonicalUrl))) {
            throw new DuplicateJobException("This official application URL is already saved");
        }
        Job job = new Job();
        job.setOwnerSubject(owner);
        job.setTitle(request.title().trim());
        job.setCompanyId(companies.findOrCreate(request.company().trim()).getId());
        job.setDescription(request.description().trim());
        job.setLocation(request.location().trim());
        job.setCountry(uaeCountry(request.location()));
        job.setCity(uaeCity(request.location()));
        job.setExperienceText(blankToNull(request.experienceText()));
        applyExperience(job, request.experienceText());
        job.setSource(JobSource.MANUAL);
        job.setSourcePortal(request.sourcePortal().trim());
        job.setSourceJobId("manual-" + sha256(canonicalUrl));
        job.setJobUrl(canonicalUrl);
        job.setCanonicalUrl(canonicalUrl);
        job.setStatus("NEW");
        return jobs.save(job);
    }

    static String canonicalPublicHttpsUrl(String raw) {
        URI uri;
        try {
            uri = URI.create(raw.trim()).normalize();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Official application URL must be a valid public HTTPS URL");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || !isPublicHostname(host)) {
            throw new IllegalArgumentException("Official application URL must be a valid public HTTPS URL");
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "" : uri.getRawPath().replaceAll("/+$", "");
        String query = canonicalQuery(uri.getRawQuery());
        return URI.create("https://" + host.toLowerCase(Locale.ROOT) + path
                + (query.isBlank() ? "" : "?" + query)).toString();
    }

    private static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return "";
        return Arrays.stream(rawQuery.split("&"))
                .filter(part -> {
                    String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    return !key.startsWith("utm_") && !java.util.Set.of("source", "ref", "referrer").contains(key);
                })
                .sorted()
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static boolean isPublicHostname(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        return value.contains(".") && !value.equals("localhost") && !value.endsWith(".local")
                && !value.endsWith(".internal") && !value.matches("[0-9.]+") && !value.contains(":");
    }

    private static void applyExperience(Job job, String value) {
        String text = value == null ? "" : value;
        Matcher range = RANGE.matcher(text);
        if (range.find()) {
            job.setExperienceMin(Integer.parseInt(range.group(1)));
            job.setExperienceMax(Integer.parseInt(range.group(2)));
            return;
        }
        Matcher minimum = MINIMUM.matcher(text);
        if (minimum.find()) job.setExperienceMin(Integer.parseInt(minimum.group(1)));
    }

    private static String uaeCountry(String location) {
        String value = location.toLowerCase(Locale.ROOT);
        return value.contains("uae") || value.contains("united arab emirates") || value.contains("dubai")
                || value.contains("abu dhabi") || value.contains("sharjah") ? "United Arab Emirates" : null;
    }

    private static String uaeCity(String location) {
        String value = location.toLowerCase(Locale.ROOT);
        if (value.contains("abu dhabi")) return "Abu Dhabi";
        if (value.contains("sharjah")) return "Sharjah";
        if (value.contains("dubai")) return "Dubai";
        return null;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("Unable to create safe job fingerprint", exception); }
    }
}
