package career.assistant.document.controller;

import career.assistant.document.entity.CoverLetter;
import career.assistant.document.repository.CoverLetterRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DocumentController {

    private final CoverLetterRepository letters;

    public DocumentController(CoverLetterRepository letters) {
        this.letters = letters;
    }

    @GetMapping("/api/cover-letters")
    List<CoverLetter> letters() {
        return letters.findAll();
    }

    @PostMapping("/api/cover-letters")
    CoverLetter letter(@Valid @RequestBody LetterRequest request) {
        CoverLetter letter = new CoverLetter();
        letter.setTitle(request.title());
        letter.setContent(request.content());
        letter.setCustomized(request.customized());
        letter.setCustomizationSummary(request.customizationSummary());
        return letters.save(letter);
    }

    public record LetterRequest(
            @NotBlank String title,
            @NotBlank String content,
            boolean customized,
            String customizationSummary
    ) {
    }
}
