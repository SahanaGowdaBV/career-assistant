package career.assistant.document.customization;

import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TruthfulnessValidator {

    private static final Pattern VERIFIED_NUMERIC_CLAIM = Pattern.compile(
            "(?i)(?:[$€£]|aed\\s*)?\\b\\d+(?:[,.]\\d+)*(?:%|\\+?\\s+years?|\\s*(?:million|billion|k|m))?"
    );

    public void validate(ParsedResume master, ParsedResume candidate, String masterText, String candidateText) {
        requireSubset("employer", employers(candidate), employers(master));
        requireSubset("job title", titles(candidate), titles(master));
        requireSubset("employment date", dates(candidate), dates(master));
        requireSubset("skill", candidate.skills(), master.skills());
        requireSubset("certification", candidate.certifications(), master.certifications());
        requireSubset("achievement", candidate.achievements(), master.achievements());
        requireSubset("metric or years-of-experience claim", numericClaims(candidateText), numericClaims(masterText));
    }

    private Set<String> employers(ParsedResume resume) {
        return experienceValues(resume, Value.EMPLOYER);
    }

    private Set<String> titles(ParsedResume resume) {
        return experienceValues(resume, Value.TITLE);
    }

    private Set<String> dates(ParsedResume resume) {
        return experienceValues(resume, Value.DATES);
    }

    private Set<String> experienceValues(ParsedResume resume, Value value) {
        Set<String> values = new HashSet<>();
        for (ExperienceEntry entry : resume.experience()) {
            String selected = switch (value) {
                case EMPLOYER -> entry.employer();
                case TITLE -> entry.jobTitle();
                case DATES -> entry.employmentDates();
            };
            if (selected != null && !selected.isBlank()) {
                values.add(normalize(selected));
            }
        }
        return values;
    }

    private Set<String> numericClaims(String text) {
        Set<String> claims = new HashSet<>();
        Matcher matcher = VERIFIED_NUMERIC_CLAIM.matcher(text == null ? "" : text);
        while (matcher.find()) {
            claims.add(normalize(matcher.group()));
        }
        return claims;
    }

    private void requireSubset(String label, Collection<String> candidate, Collection<String> master) {
        Set<String> verified = new HashSet<>();
        master.stream().filter(value -> value != null && !value.isBlank()).map(this::normalize).forEach(verified::add);
        for (String value : candidate) {
            if (value != null && !value.isBlank() && !verified.contains(normalize(value))) {
                throw new UntruthfulCustomizationException("Customized resume contains an unverified " + label + ": " + value);
            }
        }
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private enum Value { EMPLOYER, TITLE, DATES }
}
