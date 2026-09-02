package career.assistant.application.controller;
import career.assistant.application.service.ApplicationWorkflowService;import jakarta.validation.Valid;import jakarta.validation.constraints.NotNull;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/applications") public class ApplicationController {
 private final ApplicationWorkflowService service;public ApplicationController(ApplicationWorkflowService service){this.service=service;}
 @GetMapping public List<ApplicationWorkflowService.WorkflowResponse> list(){return service.list();}
 @GetMapping("/{id}")public ApplicationWorkflowService.WorkflowResponse detail(@PathVariable UUID id){return service.detail(id);}
 @PostMapping("/packages")public ResponseEntity<ApplicationWorkflowService.WorkflowResponse> generate(@Valid @RequestBody GenerateRequest r){return ResponseEntity.status(201).body(service.generate(r.jobId(),r.lowConfidenceConfirmed()));}
 @PostMapping("/{id}/regenerate")public ApplicationWorkflowService.WorkflowResponse regenerate(@PathVariable UUID id){return service.regenerate(id);}
 @GetMapping("/{id}/dry-run-preview")public ApplicationWorkflowService.DryRunPreview preview(@PathVariable UUID id){return service.preview(id);}
 @PatchMapping("/{id}/review")public ApplicationWorkflowService.WorkflowResponse review(@PathVariable UUID id,@Valid @RequestBody ReviewRequest r){return service.review(id,r.approve(),r.reason());}
 @PatchMapping("/{id}/return-to-review")public ApplicationWorkflowService.WorkflowResponse returnToReview(@PathVariable UUID id,@RequestBody(required=false)ReturnRequest r){return service.returnToReview(id,r==null?null:r.reason());}
 @PatchMapping("/{id}/manually-applied")public ApplicationWorkflowService.WorkflowResponse manuallyApplied(@PathVariable UUID id,@Valid @RequestBody ManualAppliedRequest r){return service.markManuallyApplied(id,r.confirmed(),r.confirmation());}
 @PostMapping("/{id}/run")public ApplicationWorkflowService.WorkflowResponse run(@PathVariable UUID id){return service.run(id);}
 public record GenerateRequest(@NotNull UUID jobId,boolean lowConfidenceConfirmed){}public record ReviewRequest(boolean approve,String reason){}public record ReturnRequest(String reason){}public record ManualAppliedRequest(boolean confirmed,String confirmation){}
}
