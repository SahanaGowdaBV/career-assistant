package career.assistant.document.service;

import career.assistant.document.model.ParsedResume;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Component
public class ResumeJsonCodec {

    private final ObjectMapper objectMapper;

    public ResumeJsonCodec() {
        this(new ObjectMapper());
    }

    public ResumeJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Resume data could not be serialized", exception);
        }
    }

    public ParsedResume readResume(String json) {
        if (json == null || json.isBlank()) return emptyResume();
        try {
            ParsedResume parsed = objectMapper.readValue(json, ParsedResume.class);
            return new ParsedResume(parsed.name(), parsed.contact(), parsed.professionalSummary(), parsed.experience(),
                    normalizeSkills(parsed.skills()), parsed.certifications(), parsed.education(), parsed.achievements());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored resume data is invalid", exception);
        }
    }

    private List<String> normalizeSkills(List<String> values) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String value : values == null ? List.<String>of() : values) {
            for (String part : value.split("[,;|()]+")) {
                String skill = part.trim();
                if (!skill.isBlank() && !skill.contains(":")) result.putIfAbsent(skill.toLowerCase(Locale.ROOT), skill);
            }
        }
        return List.copyOf(result.values());
    }

    public List<String> readSkills(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored resume skills are invalid", exception);
        }
    }

    private ParsedResume emptyResume() {
        return new ParsedResume(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
