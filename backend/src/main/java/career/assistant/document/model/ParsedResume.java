package career.assistant.document.model;

import java.util.List;

public record ParsedResume(
        String name,
        String professionalSummary,
        List<ExperienceEntry> experience,
        List<String> skills,
        List<String> certifications,
        List<String> education,
        List<String> achievements
) {
    public ParsedResume {
        experience = experience == null ? List.of() : List.copyOf(experience);
        skills = skills == null ? List.of() : List.copyOf(skills);
        certifications = certifications == null ? List.of() : List.copyOf(certifications);
        education = education == null ? List.of() : List.copyOf(education);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
    }
}
