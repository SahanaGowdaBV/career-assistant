package career.assistant.document.customization;

import career.assistant.document.model.ParsedResume;

import java.util.List;

public record CustomizedResumeDocument(
        byte[] content,
        String text,
        ParsedResume structured,
        List<String> emphasizedSkills,
        List<String> selectedAchievements
) {
    public CustomizedResumeDocument {
        content = content.clone();
        emphasizedSkills = List.copyOf(emphasizedSkills);
        selectedAchievements = List.copyOf(selectedAchievements);
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
