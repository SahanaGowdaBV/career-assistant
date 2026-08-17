package career.assistant.cleanup;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cleanup")
public class RetentionCleanupController {

    private final RetentionCleanupService service;

    public RetentionCleanupController(RetentionCleanupService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public RetentionCleanupService.CleanupResult preview() {
        return service.preview();
    }
}
