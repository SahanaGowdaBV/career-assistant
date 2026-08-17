package career.assistant.document.model;

import java.util.List;

public record ExperienceEntry(
        String employer,
        String jobTitle,
        String employmentDates,
        List<String> highlights
) {
    public ExperienceEntry {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }
}
