package career.assistant.jobscore.service;

import career.assistant.document.entity.ResumeVersion;
import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.parsing.SkillCatalog;
import career.assistant.document.repository.ResumeVersionRepository;
import career.assistant.document.service.ResumeJsonCodec;
import career.assistant.job.entity.Job;
import career.assistant.job.repository.JobRepository;
import career.assistant.jobscore.entity.JobScore;
import career.assistant.jobscore.repository.JobScoreRepository;
import career.assistant.security.AuthenticatedOwner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JobScoringService {

    private static final int TARGET_MIN_EXPERIENCE = 4;
    private static final int TARGET_MAX_EXPERIENCE = 9;
    private static final long PRIORITY_JOB_HOURS = 24;
    private static final long MAX_JOB_AGE_HOURS = 48;
    private static final int MINIMUM_COMPLETE_DESCRIPTION_LENGTH = 60;
    private static final BigDecimal LOW_CONFIDENCE_CAP = new BigDecimal("70.00");
    private static final BigDecimal INSUFFICIENT_EVIDENCE_CAP = new BigDecimal("90.00");
    private static final BigDecimal MEANINGFUL_COMPONENT_SCORE = new BigDecimal("70.00");
    private static final List<String> TARGET_KEYWORDS = List.of(
            "AWS", "Azure", "Kubernetes", "Docker", "Terraform", "GitHub Actions", "CI/CD", "Jenkins", "Linux"
    );
    private static final Set<String> TITLE_STOP_WORDS = Set.of(
            "senior", "junior", "lead", "principal", "staff", "the", "and", "for", "specialist", "manager"
    );
    private static final Set<String> KEYWORD_STOP_WORDS = Set.of(
            "with", "that", "this", "from", "your", "will", "have", "years", "role", "team", "work", "using",
            "required", "preferred", "experience", "responsibilities", "skills", "about", "into", "their", "they"
    );
    private static final Pattern DATE_PART = Pattern.compile(
            "(?i)(?:(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?((?:19|20)\\d{2}|present|current|now)"
    );

    private final JobScoreRepository jobScoreRepository;
    private final ResumeVersionRepository resumeRepository;
    private final ResumeJsonCodec resumeJson;
    private final JobRepository jobRepository;

    public JobScoringService(JobScoreRepository jobScoreRepository) {
        this(jobScoreRepository, null, null, null);
    }

    public JobScoringService(
            JobScoreRepository jobScoreRepository,
            ResumeVersionRepository resumeRepository,
            ResumeJsonCodec resumeJson
    ) {
        this(jobScoreRepository, resumeRepository, resumeJson, null);
    }

    @Autowired
    public JobScoringService(
            JobScoreRepository jobScoreRepository,
            ResumeVersionRepository resumeRepository,
            ResumeJsonCodec resumeJson,
            JobRepository jobRepository
    ) {
        this.jobScoreRepository = jobScoreRepository;
        this.resumeRepository = resumeRepository;
        this.resumeJson = resumeJson;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public JobScore scoreJob(Job job) {
        Optional<ResumeVersion> master = activeMaster();
        return master.isPresent() ? scoreAgainstMaster(job, master.get()) : scoreFallback(job);
    }

    public JobScore findOrScore(Job job) {
        if (activeMaster().isPresent()) return scoreJob(job);
        String owner=currentOwner();
        return (owner==null?jobScoreRepository.findByJob(job):jobScoreRepository.findByJobAndOwnerSubject(job,owner)).orElseGet(() -> scoreJob(job));
    }

    private JobScore scoreAgainstMaster(Job job, ResumeVersion masterEntity) {
        ParsedResume resume = resumeJson.readResume(masterEntity.getStructuredExperience());
        String jobText = buildSearchableText(job);
        String resumeText = nullToEmpty(masterEntity.getParsedText()).toLowerCase(Locale.ROOT);
        ClassifiedSkills jobSkills = classifySkills(jobText);
        ScoringConfidence confidence = scoringConfidence(job, jobSkills);
        Set<String> verified = normalized(resume.skills());

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : jobSkills.all()) {
            if (verified.contains(skill.toLowerCase(Locale.ROOT)) || SkillCatalog.containsSkill(resumeText, skill)) matched.add(skill);
            else missing.add(skill);
        }

        BigDecimal title = percent(targetTitleMatch(job.getTitle(), resume));
        BigDecimal required = percent(skillCoverage(jobSkills.required(), verified, resumeText));
        BigDecimal preferred = percent(skillCoverage(jobSkills.preferred(), verified, resumeText));
        BigDecimal experience = percent(experienceCompatibility(job, resume));
        BigDecimal location = calculateLocationScore(job.getCountry(), job.getCity(), job.getLocation());
        BigDecimal keywords = percent(keywordCoverage(jobText, resumeText));
        BigDecimal skills = required.multiply(new BigDecimal("0.7142857"))
                .add(preferred.multiply(new BigDecimal("0.2857143"))).setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = title.multiply(new BigDecimal("0.20"))
                .add(required.multiply(new BigDecimal("0.25")))
                .add(preferred.multiply(new BigDecimal("0.10")))
                .add(experience.multiply(new BigDecimal("0.15")))
                .add(location.multiply(new BigDecimal("0.10")))
                .add(keywords.multiply(new BigDecimal("0.20")))
                .setScale(2, RoundingMode.HALF_UP);
        total = calibratedTotal(total, confidence, matched, title, skills, experience, location, keywords);

        JobScore score = existingScore(job);
        score.setOwnerSubject(masterEntity.getOwnerSubject());
        score.setJob(job);
        score.setScore(total.min(new BigDecimal("100.00")));
        score.setScoringConfidence(confidence.name());
        score.setSkillsScore(skills);
        score.setExperienceScore(experience);
        score.setLocationScore(location);
        score.setSalaryScore(BigDecimal.ZERO);
        score.setTargetTitleScore(title);
        score.setRequiredSkillsScore(required);
        score.setPreferredSkillsScore(preferred);
        score.setKeywordCoverageScore(keywords);
        score.setMatchedKeywords(String.join(", ", matched));
        score.setMissingKeywords(confidence == ScoringConfidence.LOW
                ? "Requirements unavailable" : String.join(", ", missing));
        score.setScoringReason("Data confidence " + confidence + ". Active master v" + masterEntity.getVersionNumber()
                + " — target title " + title + "% (20%), required skills " + required + "% (25%), preferred skills "
                + preferred + "% (10%), experience " + experience + "% (15%), UAE location " + location
                + "% (10%), keyword coverage " + keywords + "% (20%). Matched skills: "
                + displayList(matched) + "; missing skills: " + displayList(missing) + ".");
        enforceScoreEligibility(job, confidence, score.getScore());
        return jobScoreRepository.save(score);
    }

    private JobScore scoreFallback(Job job) {
        String searchableText = buildSearchableText(job);
        ClassifiedSkills classifiedSkills = classifySkills(searchableText);
        ScoringConfidence confidence = scoringConfidence(job, classifiedSkills);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String keyword : TARGET_KEYWORDS) {
            if (searchableText.contains(keyword.toLowerCase(Locale.ROOT))) matched.add(keyword);
            else missing.add(keyword);
        }

        BigDecimal skillsScore = calculateFallbackSkillsScore(matched.size());
        BigDecimal experienceScore = calculateFallbackExperienceScore(job.getExperienceMin(), job.getExperienceMax());
        BigDecimal locationScore = calculateLocationScore(job.getCountry(), job.getCity(), null);
        BigDecimal salaryScore = calculateSalaryScore(job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency());
        BigDecimal totalScore = skillsScore.multiply(BigDecimal.valueOf(0.50))
                .add(experienceScore.multiply(BigDecimal.valueOf(0.20)))
                .add(locationScore.multiply(BigDecimal.valueOf(0.15)))
                .add(salaryScore.multiply(BigDecimal.valueOf(0.15)))
                .setScale(2, RoundingMode.HALF_UP);
        totalScore = calibratedTotal(totalScore, confidence, matched, BigDecimal.ZERO, skillsScore,
                experienceScore, locationScore, BigDecimal.ZERO);

        JobScore score = existingScore(job);
        score.setOwnerSubject(currentOwner());
        score.setJob(job);
        score.setScore(totalScore);
        score.setScoringConfidence(confidence.name());
        score.setSkillsScore(skillsScore);
        score.setExperienceScore(experienceScore);
        score.setLocationScore(locationScore);
        score.setSalaryScore(salaryScore);
        score.setTargetTitleScore(null);
        score.setRequiredSkillsScore(null);
        score.setPreferredSkillsScore(null);
        score.setKeywordCoverageScore(null);
        score.setMatchedKeywords(String.join(", ", matched));
        score.setMissingKeywords(confidence == ScoringConfidence.LOW
                ? "Requirements unavailable" : String.join(", ", missing));
        score.setScoringReason("Data confidence " + confidence + ". Skills: " + skillsScore + ", Experience: " + experienceScore
                + ", Location: " + locationScore + ", Salary: " + salaryScore);
        enforceScoreEligibility(job, confidence, score.getScore());
        return jobScoreRepository.save(score);
    }

    private ScoringConfidence scoringConfidence(Job job, ClassifiedSkills skills) {
        String description = nullToEmpty(job.getDescription()).strip().replaceAll("\\s+", " ");
        if (description.length() < MINIMUM_COMPLETE_DESCRIPTION_LENGTH) {
            return ScoringConfidence.LOW;
        }
        String lower = description.toLowerCase(Locale.ROOT);
        boolean requirementsIdentifiable = skills.all().size() >= 2
                || lower.contains("required") || lower.contains("requirement")
                || lower.contains("qualification") || lower.contains("responsibilit");
        return requirementsIdentifiable ? ScoringConfidence.HIGH : ScoringConfidence.LOW;
    }

    private BigDecimal calibratedTotal(
            BigDecimal total,
            ScoringConfidence confidence,
            List<String> matchedSkills,
            BigDecimal title,
            BigDecimal skills,
            BigDecimal experience,
            BigDecimal location,
            BigDecimal keywords
    ) {
        if (confidence == ScoringConfidence.LOW) {
            return total.min(LOW_CONFIDENCE_CAP);
        }
        if (total.compareTo(INSUFFICIENT_EVIDENCE_CAP) <= 0) {
            return total;
        }
        long meaningfulMatches = List.of(title, skills, experience, location, keywords).stream()
                .filter(component -> component.compareTo(MEANINGFUL_COMPONENT_SCORE) >= 0)
                .count();
        return matchedSkills.size() >= 2 && meaningfulMatches >= 3
                ? total : INSUFFICIENT_EVIDENCE_CAP;
    }

    private void enforceScoreEligibility(Job job, ScoringConfidence confidence, BigDecimal total) {
        String status = job.getStatus();
        String eligibleStatus = status;
        if (confidence == ScoringConfidence.LOW) {
            if (!"PENDING_REVIEW".equals(status)) {
                eligibleStatus = "NEW";
            }
        } else if (status == null || "NEW".equals(status) || "HIGH_SCORE".equals(status)) {
            eligibleStatus = total.compareTo(new BigDecimal("75.00")) >= 0 ? "HIGH_SCORE" : "NEW";
        }
        if (!java.util.Objects.equals(status, eligibleStatus)) {
            job.setStatus(eligibleStatus);
            if (jobRepository != null) {
                jobRepository.save(job);
            }
        }
    }

    public boolean isFreshJob(Job job) {
        if (job == null || job.getPostedAt() == null) return false;
        return !job.getPostedAt().isBefore(OffsetDateTime.now().minusHours(MAX_JOB_AGE_HOURS));
    }

    public boolean isPriorityFreshJob(Job job) {
        if (job == null || job.getPostedAt() == null) return false;
        return !job.getPostedAt().isBefore(OffsetDateTime.now().minusHours(PRIORITY_JOB_HOURS));
    }

    public long getJobAgeHours(Job job) {
        if (job == null || job.getPostedAt() == null) return -1;
        return Duration.between(job.getPostedAt(), OffsetDateTime.now()).toHours();
    }

    private Optional<ResumeVersion> activeMaster() {
        if (resumeRepository == null || resumeJson == null) return Optional.empty();
        String owner=currentOwner();
        return (owner==null?resumeRepository.findFirstByMasterResumeTrue():resumeRepository.findFirstByMasterResumeTrueAndOwnerSubject(owner))
                .filter(resume -> resume.getStructuredExperience() != null && resume.getParsedText() != null);
    }

    private JobScore existingScore(Job job) {
        String owner=currentOwner();
        return (owner==null?jobScoreRepository.findByJob(job):jobScoreRepository.findByJobAndOwnerSubject(job,owner)).orElseGet(JobScore::new);
    }
    private String currentOwner(){if(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()==null)return null;return AuthenticatedOwner.required();}

    private String buildSearchableText(Job job) {
        return (nullToEmpty(job.getTitle()) + "\n" + nullToEmpty(job.getDescription())).toLowerCase(Locale.ROOT);
    }

    private ClassifiedSkills classifySkills(String jobText) {
        List<String> all = SkillCatalog.findMentionedSkills(jobText);
        List<String> preferred = new ArrayList<>();
        for (String line : jobText.split("\\R|[.!?]")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("preferred") || lower.contains("nice to have") || lower.contains("bonus")) {
                preferred.addAll(SkillCatalog.findMentionedSkills(line));
            }
        }
        preferred = new ArrayList<>(new LinkedHashSet<>(preferred));
        List<String> required = new ArrayList<>(all);
        required.removeAll(preferred);
        return new ClassifiedSkills(List.copyOf(required), List.copyOf(preferred));
    }

    private double skillCoverage(List<String> requested, Set<String> verified, String resumeText) {
        if (requested.isEmpty()) return 100;
        long matched = requested.stream().filter(skill -> verified.contains(skill.toLowerCase(Locale.ROOT))
                || SkillCatalog.containsSkill(resumeText, skill)).count();
        return matched * 100.0 / requested.size();
    }

    private double targetTitleMatch(String jobTitle, ParsedResume resume) {
        if (jobTitle == null || jobTitle.isBlank()) return 0;
        List<String> candidateTitles = resume.experience().stream()
                .map(ExperienceEntry::jobTitle).filter(value -> value != null && !value.isBlank()).toList();
        if (candidateTitles.isEmpty() && resume.professionalSummary() != null) {
            candidateTitles = List.of(resume.professionalSummary());
        }
        Set<String> target = titleTokens(jobTitle);
        return candidateTitles.stream().map(this::titleTokens).mapToDouble(candidate -> dice(target, candidate)).max().orElse(0) * 100;
    }

    private Set<String> titleTokens(String title) {
        Set<String> values = new HashSet<>();
        for (String value : title.toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+")) {
            if (value.length() >= 3 && !TITLE_STOP_WORDS.contains(value)) values.add(value);
        }
        return values;
    }

    private double dice(Set<String> first, Set<String> second) {
        if (first.isEmpty() || second.isEmpty()) return 0;
        long common = first.stream().filter(second::contains).count();
        return 2.0 * common / (first.size() + second.size());
    }

    private double experienceCompatibility(Job job, ParsedResume resume) {
        if (job.getExperienceMin() == null && job.getExperienceMax() == null) return 100;
        double years = verifiedExperienceYears(resume.experience());
        if (years == 0) return 50;
        int minimum = job.getExperienceMin() == null ? 0 : job.getExperienceMin();
        int maximum = job.getExperienceMax() == null ? Integer.MAX_VALUE : job.getExperienceMax();
        if (years >= minimum && years <= maximum + 2) return 100;
        double distance = years < minimum ? minimum - years : years - maximum;
        if (distance <= 1) return 75;
        if (distance <= 2) return 50;
        return 20;
    }

    private double verifiedExperienceYears(List<ExperienceEntry> entries) {
        List<MonthRange> ranges = entries.stream().map(ExperienceEntry::employmentDates)
                .map(this::parseRange).flatMap(Optional::stream).sorted(Comparator.comparing(MonthRange::start)).toList();
        if (ranges.isEmpty()) return 0;
        long months = 0;
        YearMonth start = ranges.getFirst().start();
        YearMonth end = ranges.getFirst().end();
        for (MonthRange range : ranges.subList(1, ranges.size())) {
            if (!range.start().isAfter(end.plusMonths(1))) {
                if (range.end().isAfter(end)) end = range.end();
            } else {
                months += ChronoUnit.MONTHS.between(start, end) + 1;
                start = range.start();
                end = range.end();
            }
        }
        months += ChronoUnit.MONTHS.between(start, end) + 1;
        return months / 12.0;
    }

    private Optional<MonthRange> parseRange(String value) {
        if (value == null) return Optional.empty();
        Matcher matcher = DATE_PART.matcher(value);
        List<YearMonth> dates = new ArrayList<>();
        while (matcher.find()) {
            String yearValue = matcher.group(2).toLowerCase(Locale.ROOT);
            if (Set.of("present", "current", "now").contains(yearValue)) dates.add(YearMonth.now());
            else dates.add(YearMonth.of(Integer.parseInt(yearValue), month(matcher.group(1))));
        }
        return dates.size() >= 2 ? Optional.of(new MonthRange(dates.get(0), dates.get(1))) : Optional.empty();
    }

    private int month(String value) {
        if (value == null) return 1;
        String shortMonth = value.substring(0, 3).toLowerCase(Locale.ROOT);
        return switch (shortMonth) {
            case "feb" -> 2;
            case "mar" -> 3;
            case "apr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dec" -> 12;
            default -> 1;
        };
    }

    private double keywordCoverage(String jobText, String resumeText) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : jobText.split("[^a-z0-9+#.]+")) {
            if (token.length() >= 4 && !KEYWORD_STOP_WORDS.contains(token)) keywords.add(token);
        }
        if (keywords.isEmpty()) return 100;
        long covered = keywords.stream().filter(resumeText::contains).count();
        return covered * 100.0 / keywords.size();
    }

    private Set<String> normalized(List<String> values) {
        Set<String> normalized = new HashSet<>();
        values.forEach(value -> normalized.add(value.toLowerCase(Locale.ROOT)));
        return normalized;
    }

    private BigDecimal percent(double value) {
        return BigDecimal.valueOf(Math.max(0, Math.min(100, value))).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateFallbackSkillsScore(int matchedCount) {
        return percent((matchedCount * 100.0) / TARGET_KEYWORDS.size());
    }

    private BigDecimal calculateFallbackExperienceScore(Integer min, Integer max) {
        if (min == null && max == null) return BigDecimal.ZERO;
        int jobMin = min == null ? max : min;
        int jobMax = max == null ? min : max;
        return jobMin <= TARGET_MAX_EXPERIENCE && jobMax >= TARGET_MIN_EXPERIENCE
                ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
    }

    private BigDecimal calculateLocationScore(String country, String city, String fullLocation) {
        String location = (nullToEmpty(country) + " " + nullToEmpty(city) + " " + nullToEmpty(fullLocation)).toLowerCase(Locale.ROOT);
        return location.contains("uae") || location.contains("united arab emirates") || location.contains("dubai")
                || location.contains("abu dhabi") || location.contains("sharjah")
                ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
    }

    private BigDecimal calculateSalaryScore(BigDecimal min, BigDecimal max, String currency) {
        if (min == null && max == null) return BigDecimal.ZERO;
        if (currency == null || !currency.equalsIgnoreCase("AED")) return BigDecimal.ZERO;
        BigDecimal salary = max != null ? max : min;
        if (salary.compareTo(BigDecimal.valueOf(25000)) >= 0) return BigDecimal.valueOf(100);
        if (salary.compareTo(BigDecimal.valueOf(20000)) >= 0) return BigDecimal.valueOf(80);
        if (salary.compareTo(BigDecimal.valueOf(15000)) >= 0) return BigDecimal.valueOf(60);
        return BigDecimal.valueOf(40);
    }

    private String displayList(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ClassifiedSkills(List<String> required, List<String> preferred) {
        List<String> all() {
            List<String> all = new ArrayList<>(required);
            all.addAll(preferred);
            return all;
        }
    }

    private record MonthRange(YearMonth start, YearMonth end) {
    }

    private enum ScoringConfidence {
        LOW,
        HIGH
    }
}
