package career.assistant.application.controller;
import career.assistant.application.service.ApplicationWorkflowService; import career.assistant.application.service.AutoApplyExecutionService; import org.springframework.web.bind.annotation.*; import java.util.UUID;
@RestController @RequestMapping("/api/auto-apply/applications") public class AutoApplyController {
 private final ApplicationWorkflowService workflow; private final AutoApplyExecutionService execution; private final career.assistant.application.service.AutoApplyWorkerService worker;
 public AutoApplyController(ApplicationWorkflowService w,AutoApplyExecutionService e,career.assistant.application.service.AutoApplyWorkerService x){workflow=w;execution=e;worker=x;}
 @PostMapping("/{id}/preview") public ApplicationWorkflowService.DryRunPreview preview(@PathVariable UUID id){return workflow.preview(id);}
 @PostMapping("/{id}/execute") public AutoApplyExecutionService.ExecutionResult execute(@PathVariable UUID id,@RequestParam(defaultValue="false") boolean confirmation){return execution.execute(id,confirmation);}
 @PostMapping("/worker/run") public career.assistant.application.service.AutoApplyWorkerService.WorkerResult worker(@RequestParam(defaultValue="false") boolean confirmation){return worker.run(confirmation);}
}
