package career.assistant.document.model;

public record ResumeContact(
        String email,
        String phone,
        String linkedin,
        String github,
        String location
) {
    public ResumeContact(String email, String phone, String linkedin, String location) {
        this(email, phone, linkedin, null, location);
    }
}
