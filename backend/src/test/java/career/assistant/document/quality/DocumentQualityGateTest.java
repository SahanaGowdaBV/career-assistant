package career.assistant.document.quality;

import career.assistant.document.model.ExperienceEntry;
import career.assistant.document.model.ParsedResume;
import career.assistant.document.model.ResumeContact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentQualityGateTest {
    private final DocumentQualityGate gate = new DocumentQualityGate();

    @Test
    void acceptsEmphasisChangesWhilePreservingAtomicReverseChronologicalEntries() {
        ParsedResume master = resume(List.of(current(), earlier()), List.of("AWS", "Terraform"));
        ParsedResume candidate = resume(List.of(
                new ExperienceEntry("Current Example", "Senior Platform Engineer", "Jan 2022 - Present", List.of("Improved reliable delivery automation.", "Built production AWS platforms.")),
                earlier()
        ), List.of("Terraform", "AWS"));

        assertDoesNotThrow(() -> gate.validateResume(master, candidate, "safe resume text"));
    }

    @Test
    void blocksMixedJobsMissingIdentityFragmentsAndUnsupportedSkills() {
        ParsedResume master = resume(List.of(current(), earlier()), List.of("AWS", "Terraform"));
        ParsedResume swapped = resume(List.of(earlier(), current()), List.of("AWS", "Terraform"));
        ParsedResume fragment = resume(List.of(
                new ExperienceEntry("Current Example", "Senior Platform Engineer", "Jan 2022 - Present", List.of("Built production", "Improved reliable delivery automation.")),
                earlier()
        ), List.of("AWS", "Terraform"));
        ParsedResume unsupported = resume(List.of(current(), earlier()), List.of("AWS", "Azure"));
        ParsedResume missingEmail = new ParsedResume("Jane Example", new ResumeContact(null, "+971 50 123 4567", null, null),
                "Platform engineer.", List.of(current(), earlier()), List.of("AWS", "Terraform"), List.of(), List.of(), List.of());

        assertThrows(DocumentQualityException.class, () -> gate.validateResume(master, swapped, "text"));
        assertThrows(DocumentQualityException.class, () -> gate.validateResume(master, fragment, "text"));
        assertThrows(DocumentQualityException.class, () -> gate.validateResume(master, unsupported, "text"));
        assertThrows(DocumentQualityException.class, () -> gate.validateResume(missingEmail, missingEmail, "text"));
    }

    private ParsedResume resume(List<ExperienceEntry> experience, List<String> skills) {
        return new ParsedResume("Jane Example", new ResumeContact("jane@example.com", "+971 50 123 4567", "linkedin.com/in/jane", "Dubai, UAE"),
                "Platform engineer.", experience, skills, List.of(), List.of(), List.of());
    }
    private ExperienceEntry current() {
        return new ExperienceEntry("Current Example", "Senior Platform Engineer", "Jan 2022 - Present", List.of("Built production AWS platforms.", "Improved reliable delivery automation."));
    }
    private ExperienceEntry earlier() {
        return new ExperienceEntry("Earlier Example", "Platform Engineer", "Jan 2020 - Dec 2021", List.of("Maintained production Terraform systems."));
    }
}
