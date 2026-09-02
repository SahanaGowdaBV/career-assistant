package career.assistant.application.controller;
import career.assistant.application.service.ApplicationWorkflowService; import career.assistant.document.service.ResumeConflictException; import org.springframework.beans.factory.annotation.Value; import org.springframework.web.bind.annotation.*; import java.util.UUID;
@RestController @RequestMapping("/api/auto-apply/applications") public class AutoApplyController {
 private final ApplicationWorkflowService workflow; private final boolean enabled; private final boolean dryRun;
 public AutoApplyController(ApplicationWorkflowService w,@Value("${career.application.auto-apply-enabled:false}") boolean e,@Value("${career.application.dry-run:true}") boolean d){workflow=w;enabled=e;dryRun=d;}
 @PostMapping("/{id}/preview") public ApplicationWorkflowService.DryRunPreview preview(@PathVariable UUID id){return workflow.preview(id);}
 @PostMapping("/{id}/execute") public ApplicationWorkflowService.WorkflowResponse execute(@PathVariable UUID id){if(!enabled||dryRun)throw new ResumeConflictException("Real auto-apply is disabled; use the dry-run preview");return workflow.run(id);}
}
