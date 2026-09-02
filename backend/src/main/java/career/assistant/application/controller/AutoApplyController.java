package career.assistant.application.controller;
import career.assistant.application.service.ApplicationWorkflowService; import career.assistant.application.service.AutoApplyExecutionService; import org.springframework.web.bind.annotation.*; import java.util.UUID;
@RestController @RequestMapping("/api/auto-apply/applications") public class AutoApplyController {
 private final ApplicationWorkflowService workflow; private final AutoApplyExecutionService execution;
 public AutoApplyController(ApplicationWorkflowService w,AutoApplyExecutionService e){workflow=w;execution=e;}
 @PostMapping("/{id}/preview") public ApplicationWorkflowService.DryRunPreview preview(@PathVariable UUID id){return workflow.preview(id);}
 @PostMapping("/{id}/execute") public AutoApplyExecutionService.ExecutionResult execute(@PathVariable UUID id,@RequestParam(defaultValue="false") boolean confirmation){return execution.execute(id,confirmation);}
}
