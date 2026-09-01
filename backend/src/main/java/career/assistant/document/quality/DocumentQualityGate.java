package career.assistant.document.quality;

import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ResumeContact;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocumentQualityGate {

    private static final Pattern START_YEAR = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern NUMBER = Pattern.compile("(?i)(?:[$€£]|aed\\s*)?\\b\\d+(?:[,.]\\d+)*(?:%|\\+?\\s+years?|\\s*(?:million|billion|k|m))?");

    public void validateResume(ParsedResume master, ParsedResume candidate, String renderedText) {
        requireIdentity(master.name(), "name");
        requireIdentity(master.contact().email(), "email");
        requireIdentity(master.contact().phone(), "phone");
        requireSame("name", master.name(), candidate.name());
        requireContact(master.contact(), candidate.contact());
        requireSame("professional summary", master.professionalSummary(), candidate.professionalSummary());
        requireAtomicChronology(master.experience(), candidate.experience());
        requireExactValues("skills", master.skills(), candidate.skills());
        requireExactValues("certifications", master.certifications(), candidate.certifications());
        requireExactValues("education", master.education(), candidate.education());
        requireExactValues("achievements", master.achievements(), candidate.achievements());
        validateSkills(candidate.skills());
        validateHighlights(candidate.experience());
        if (wordCount(renderedText) > 1_100) fail("Resume exceeds the safe two-page content budget");
    }

    public void validateCoverLetter(
            ParsedResume master,
            String role,
            String company,
            String content,
            List<String> sourcedClaims
    ) {
        int words = wordCount(content);
        if (words < 250 || words > 350) fail("Cover letter must contain 250-350 words; found " + words);
        if (blank(role) || !content.contains(role)) fail("Cover letter is missing the job role");
        if (blank(company) || !content.contains(company)) fail("Cover letter is missing the company name");
        if (sourcedClaims.isEmpty()) fail("Cover letter has no resume-backed experience evidence");
        Set<String> source = factualSource(master);
        for (String claim : sourcedClaims) {
            if (!source.contains(normalize(claim)) || !content.contains(claim)) fail("Cover letter contains an unsupported factual claim");
        }
        Set<String> allowedNumbers = numericClaims(String.join(" ", source));
        allowedNumbers.addAll(numericClaims(role + " " + company));
        if (!allowedNumbers.containsAll(numericClaims(content))) fail("Cover letter contains an unsupported numeric claim");
        if (content.toLowerCase(Locale.ROOT).contains("verified experience")
                || content.toLowerCase(Locale.ROOT).contains("exactly as verified")) {
            fail("Cover letter contains compliance-style wording");
        }
    }

    private void requireContact(ResumeContact master, ResumeContact candidate) {
        requireSame("email", master.email(), candidate.email());
        requireSame("phone", master.phone(), candidate.phone());
        requireSame("LinkedIn", master.linkedin(), candidate.linkedin());
        requireSame("location", master.location(), candidate.location());
    }

    private void requireAtomicChronology(List<ExperienceEntry> master, List<ExperienceEntry> candidate) {
        if (master.isEmpty()) fail("Master resume has no structured employment entries");
        if (master.size() != candidate.size()) fail("Employment entries were added, removed, or mixed");
        int previousStart = Integer.MAX_VALUE;
        for (int i = 0; i < master.size(); i++) {
            ExperienceEntry source = master.get(i);
            ExperienceEntry output = candidate.get(i);
            requireIdentity(source.employer(), "employer in employment entry " + (i + 1));
            requireIdentity(source.jobTitle(), "title in employment entry " + (i + 1));
            requireIdentity(source.employmentDates(), "dates in employment entry " + (i + 1));
            requireSame("employer", source.employer(), output.employer());
            requireSame("job title", source.jobTitle(), output.jobTitle());
            requireSame("employment dates", source.employmentDates(), output.employmentDates());
            requireExactValues("employment highlights", source.highlights(), output.highlights());
            Matcher matcher = START_YEAR.matcher(source.employmentDates());
            if (!matcher.find()) fail("Employment date range is not structurally recognizable: " + source.employmentDates());
            int start = Integer.parseInt(matcher.group());
            if (start > previousStart) fail("Employment chronology is not reverse-chronological");
            previousStart = start;
        }
    }

    private void validateSkills(List<String> values) {
        if (values.isEmpty()) fail("Skills are empty");
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (blank(value) || value.contains("\n") || value.contains(":")) fail("Skills contain an empty or malformed value");
            if (!seen.add(normalize(value))) fail("Skills contain a duplicate value: " + value);
        }
    }

    private void validateHighlights(List<ExperienceEntry> entries) {
        for (ExperienceEntry entry : entries) {
            if (entry.highlights().isEmpty()) fail("Employment entry has no associated responsibility or achievement bullets");
            for (String value : entry.highlights()) {
                int words = wordCount(value);
                if (words < 4 || !(value.matches(".*[.!?;:]$") || value.matches(".*\\d%$"))) {
                    fail("Employment entry contains an orphan sentence fragment: " + value);
                }
            }
        }
    }

    private void requireExactValues(String label, Collection<String> source, Collection<String> candidate) {
        List<String> normalizedSource = source.stream().map(this::normalize).sorted().toList();
        List<String> normalizedCandidate = candidate.stream().map(this::normalize).sorted().toList();
        if (!normalizedSource.equals(normalizedCandidate)) fail("Generated " + label + " cannot be traced exactly to the master resume");
    }

    private void requireSame(String label, String source, String candidate) {
        if (!normalize(source).equals(normalize(candidate))) fail("Generated " + label + " differs from the master resume");
    }

    private void requireIdentity(String value, String label) {
        if (blank(value)) fail("Master resume is missing required " + label);
    }

    private Set<String> factualSource(ParsedResume resume) {
        Set<String> values = new HashSet<>();
        add(values, resume.name());
        add(values, resume.professionalSummary());
        add(values, resume.skills());
        add(values, resume.certifications());
        add(values, resume.education());
        add(values, resume.achievements());
        for (ExperienceEntry entry : resume.experience()) {
            add(values, entry.employer()); add(values, entry.jobTitle()); add(values, entry.employmentDates()); add(values, entry.highlights());
        }
        return values;
    }

    private void add(Set<String> values, String value) { if (!blank(value)) values.add(normalize(value)); }
    private void add(Set<String> values, Collection<String> source) { source.forEach(value -> add(values, value)); }
    private Set<String> numericClaims(String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = NUMBER.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(normalize(matcher.group()));
        return result;
    }
    private int wordCount(String value) { return blank(value) ? 0 : value.trim().split("\\s+").length; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }
    private void fail(String message) { throw new DocumentQualityException("Document quality gate blocked package completion: " + message); }
}
