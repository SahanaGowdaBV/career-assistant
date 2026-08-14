package career.assistant.document.customization;

import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TruthfulnessValidatorTest {

    private final TruthfulnessValidator validator = new TruthfulnessValidator();

    @Test
    void acceptsReorderedVerifiedContent() {
        ParsedResume master = resume(List.of("AWS", "Docker"), List.of("Reduced cost by 20%."));
        ParsedResume candidate = resume(List.of("Docker", "AWS"), List.of("Reduced cost by 20%."));

        assertDoesNotThrow(() -> validator.validate(master, candidate,
                "Example Corp Jan 2020 - Present AWS Docker Reduced cost by 20%.",
                "Docker AWS Example Corp Jan 2020 - Present Reduced cost by 20%."));
    }

    @Test
    void rejectsUnverifiedSkillAndInventedMetric() {
        ParsedResume master = resume(List.of("AWS", "Docker"), List.of("Reduced cost by 20%."));
        ParsedResume unverifiedSkill = resume(List.of("AWS", "Azure"), List.of("Reduced cost by 20%."));
        ParsedResume inventedMetric = resume(List.of("AWS"), List.of("Reduced cost by 20%."));

        assertThrows(UntruthfulCustomizationException.class, () -> validator.validate(master, unverifiedSkill,
                "AWS Docker 20%", "AWS Azure 20%"));
        assertThrows(UntruthfulCustomizationException.class, () -> validator.validate(master, inventedMetric,
                "AWS Docker 20%", "AWS Reduced cost by 30%."));
    }

    private ParsedResume resume(List<String> skills, List<String> achievements) {
        return new ParsedResume(
                "Jane Example",
                "Verified summary",
                List.of(new ExperienceEntry("Example Corp", "DevOps Engineer", "Jan 2020 - Present", List.of("Built AWS systems."))),
                skills,
                List.of("AWS Certified Example"),
                List.of("Example University"),
                achievements
        );
    }
}
