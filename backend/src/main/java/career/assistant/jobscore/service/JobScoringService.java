package career.assistant.jobscore.service;

import career.assistant.job.entity.Job;
import career.assistant.jobscore.entity.JobScore;
import career.assistant.jobscore.repository.JobScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobScoringService {

    private static final int TARGET_MIN_EXPERIENCE = 4;
    private static final int TARGET_MAX_EXPERIENCE = 9;

    /*
     * Jobs posted within 24 hours are considered PRIORITY jobs.
     */
    private static final long PRIORITY_JOB_HOURS = 24;

    /*
     * Jobs older than 48 hours should not be considered
     * for automatic processing.
     */
    private static final long MAX_JOB_AGE_HOURS = 48;

    private static final List<String> TARGET_KEYWORDS = List.of(
            "AWS",
            "Azure",
            "Kubernetes",
            "Docker",
            "Terraform",
            "GitHub Actions",
            "CI/CD",
            "Jenkins",
            "Linux"
    );

    private final JobScoreRepository jobScoreRepository;

    public JobScoringService(JobScoreRepository jobScoreRepository) {
        this.jobScoreRepository = jobScoreRepository;
    }

    @Transactional
    public JobScore scoreJob(Job job) {

        String searchableText = buildSearchableText(job);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String keyword : TARGET_KEYWORDS) {

            if (searchableText.contains(keyword.toLowerCase())) {
                matched.add(keyword);
            } else {
                missing.add(keyword);
            }
        }

        BigDecimal skillsScore =
                calculateSkillsScore(matched.size());

        BigDecimal experienceScore =
                calculateExperienceScore(
                        job.getExperienceMin(),
                        job.getExperienceMax()
                );

        BigDecimal locationScore =
                calculateLocationScore(
                        job.getCountry(),
                        job.getCity()
                );

        BigDecimal salaryScore =
                calculateSalaryScore(
                        job.getSalaryMin(),
                        job.getSalaryMax(),
                        job.getSalaryCurrency()
                );

        BigDecimal totalScore =
                skillsScore
                        .multiply(BigDecimal.valueOf(0.50))
                        .add(
                                experienceScore
                                        .multiply(BigDecimal.valueOf(0.20))
                        )
                        .add(
                                locationScore
                                        .multiply(BigDecimal.valueOf(0.15))
                        )
                        .add(
                                salaryScore
                                        .multiply(BigDecimal.valueOf(0.15))
                        )
                        .setScale(2, RoundingMode.HALF_UP);

        /*
         * If this job has already been scored,
         * update the existing JobScore.
         *
         * Otherwise create a new JobScore.
         */
        JobScore jobScore = jobScoreRepository
                .findByJob(job)
                .orElseGet(JobScore::new);

        jobScore.setJob(job);
        jobScore.setScore(totalScore);
        jobScore.setSkillsScore(skillsScore);
        jobScore.setExperienceScore(experienceScore);
        jobScore.setLocationScore(locationScore);
        jobScore.setSalaryScore(salaryScore);

        jobScore.setMatchedKeywords(
                String.join(", ", matched)
        );

        jobScore.setMissingKeywords(
                String.join(", ", missing)
        );

        jobScore.setScoringReason(
                "Skills: " + skillsScore +
                ", Experience: " + experienceScore +
                ", Location: " + locationScore +
                ", Salary: " + salaryScore
        );

        return jobScoreRepository.save(jobScore);
    }

    /**
     * Returns true when the job was posted within the last 48 hours.
     *
     * Jobs without postedAt are treated as NOT fresh.
     */
    public boolean isFreshJob(Job job) {

        if (job == null || job.getPostedAt() == null) {
            return false;
        }

        OffsetDateTime cutoff =
                OffsetDateTime.now().minusHours(MAX_JOB_AGE_HOURS);

        return !job.getPostedAt().isBefore(cutoff);
    }

    /**
     * Returns true when the job was posted within the last 24 hours.
     *
     * These jobs should receive priority in the application pipeline.
     */
    public boolean isPriorityFreshJob(Job job) {

        if (job == null || job.getPostedAt() == null) {
            return false;
        }

        OffsetDateTime cutoff =
                OffsetDateTime.now().minusHours(PRIORITY_JOB_HOURS);

        return !job.getPostedAt().isBefore(cutoff);
    }

    /**
     * Returns the age of the job in hours.
     *
     * Returns -1 when postedAt is not available.
     */
    public long getJobAgeHours(Job job) {

        if (job == null || job.getPostedAt() == null) {
            return -1;
        }

        return java.time.Duration.between(
                job.getPostedAt(),
                OffsetDateTime.now()
        ).toHours();
    }

    private String buildSearchableText(Job job) {

        return (
                nullToEmpty(job.getTitle()) + " " +
                nullToEmpty(job.getDescription())
        ).toLowerCase();
    }

    private BigDecimal calculateSkillsScore(int matchedCount) {

        return BigDecimal.valueOf(
                (matchedCount * 100.0) / TARGET_KEYWORDS.size()
        ).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateExperienceScore(
            Integer min,
            Integer max
    ) {

        if (min == null && max == null) {
            return BigDecimal.ZERO;
        }

        int jobMin = min == null ? max : min;
        int jobMax = max == null ? min : max;

        if (jobMin <= TARGET_MAX_EXPERIENCE &&
                jobMax >= TARGET_MIN_EXPERIENCE) {

            return BigDecimal.valueOf(100);
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculateLocationScore(
            String country,
            String city
    ) {

        String location =
                (
                        nullToEmpty(country) + " " +
                        nullToEmpty(city)
                ).toLowerCase();

        if (location.contains("uae") ||
                location.contains("united arab emirates") ||
                location.contains("dubai") ||
                location.contains("abu dhabi") ||
                location.contains("sharjah")) {

            return BigDecimal.valueOf(100);
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculateSalaryScore(
            BigDecimal min,
            BigDecimal max,
            String currency
    ) {

        if (min == null && max == null) {
            return BigDecimal.ZERO;
        }

        if (currency == null ||
                !currency.equalsIgnoreCase("AED")) {

            return BigDecimal.ZERO;
        }

        BigDecimal salary =
                max != null ? max : min;

        if (salary.compareTo(
                BigDecimal.valueOf(25000)
        ) >= 0) {

            return BigDecimal.valueOf(100);
        }

        if (salary.compareTo(
                BigDecimal.valueOf(20000)
        ) >= 0) {

            return BigDecimal.valueOf(80);
        }

        if (salary.compareTo(
                BigDecimal.valueOf(15000)
        ) >= 0) {

            return BigDecimal.valueOf(60);
        }

        return BigDecimal.valueOf(40);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
