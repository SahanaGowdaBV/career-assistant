package career.assistant.application.controller;
import career.assistant.application.audit.*;import career.assistant.security.AuthenticatedOwner;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/submission-attempts") public class SubmissionAttemptController {private final SubmissionAttemptService service;public SubmissionAttemptController(SubmissionAttemptService service){this.service=service;}@GetMapping public List<SubmissionAttemptDto> list(){return service.listOwned(AuthenticatedOwner.required());}}
