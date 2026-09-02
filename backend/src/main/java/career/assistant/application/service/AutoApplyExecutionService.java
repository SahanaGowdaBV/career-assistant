package career.assistant.application.service;
import career.assistant.api.ResourceNotFoundException;
import career.assistant.application.audit.*;
import career.assistant.application.ats.*;
import career.assistant.application.entity.*;
import career.assistant.application.repository.ApplicationRepository;
import career.assistant.jobscore.repository.JobScoreRepository;
import career.assistant.security.AuthenticatedOwner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
@Service public class AutoApplyExecutionService {
 private final ApplicationRepository applications;private final JobScoreRepository scores;private final SubmissionAttemptService attempts;private final AshbySubmissionTransport transport;private final ApplicationNotificationService notifications;private final AutoApplyEligibilityService eligibility;private final boolean enabled,dryRun;
 public AutoApplyExecutionService(ApplicationRepository a,JobScoreRepository s,SubmissionAttemptService at,AshbySubmissionTransport t,ApplicationNotificationService n,career.assistant.document.service.ResumeService r,career.assistant.document.service.CoverLetterService l,AutoApplyEligibilityService eligibility,@Value("${career.application.auto-apply-enabled:false}")boolean e,@Value("${career.application.dry-run:true}")boolean d,@Value("${career.application.max-real-submissions-daily:1}")int dl,@Value("${career.application.minimum-score:75}")java.math.BigDecimal m){applications=a;scores=s;attempts=at;transport=t;notifications=n;this.eligibility=eligibility;enabled=e;dryRun=d;}
 public ExecutionResult execute(UUID id,boolean confirmation){return executeForOwner(id,confirmation,AuthenticatedOwner.required());}
 public ExecutionResult executeForOwner(UUID id,boolean confirmation,String owner){
  Application app=applications.findByIdAndOwnerSubject(id,owner).orElseThrow(()->new ResourceNotFoundException("Application not found"));
  if(!confirmation)return blocked(id,"CONFIRMATION_REQUIRED","Explicit confirmation is required");
  if(!enabled||dryRun)return blocked(id,"FEATURE_DISABLED","Real auto-apply is disabled");
  var evaluated=eligibility.evaluate(app,owner);if(!evaluated.eligible())return blocked(id,evaluated.blockerCodes().getFirst(),"Application is not eligible");
  var score=scores.findByJobAndOwnerSubject(app.getJob(),owner).orElseThrow(()->new ResourceNotFoundException("Score not found"));String url=app.getJob().getJobUrl();
  SubmissionAttemptDto claim=attempts.claim(id,owner,"ASHBY",url,"application:"+id,"ashby:"+id);
  AshbySubmissionResult result=transport.submit(new AshbySubmissionTransport.SubmissionPlan(url,null,Map.of("applicationId",id.toString()),List.of()),false);
  SubmissionAttemptState state=switch(result.outcome()){case CONFIRMED->SubmissionAttemptState.CONFIRMED;case UNCERTAIN->SubmissionAttemptState.UNCERTAIN;case REVIEW_REQUIRED,CAPTCHA_OR_CHALLENGE,SCHEMA_CHANGED->SubmissionAttemptState.REVIEW_REQUIRED;default->SubmissionAttemptState.FAILED;};
  attempts.finish(claim.id(),owner,state,result.safeReason(),result.confirmationReference(),result.confirmationUrl());
  if(result.outcome()==AshbySubmissionResult.Outcome.CONFIRMED){app.setStatus(ApplicationStatus.AUTO_APPLIED);app.setConfirmationId(result.confirmationReference());app.setConfirmationUrl(result.confirmationUrl());app.setAppliedAt(java.time.OffsetDateTime.now());app.setSubmittedAt(app.getAppliedAt());applications.save(app);try{notifications.sendVerifiedSuccessOnce(app,score.getScore().toPlainString());}catch(RuntimeException ignored){}}
  else if(state==SubmissionAttemptState.REVIEW_REQUIRED||state==SubmissionAttemptState.UNCERTAIN){app.setStatus(ApplicationStatus.PENDING_REVIEW);applications.save(app);}
  return new ExecutionResult(id,claim.id(),state,result.safeReason(),result.confirmationReference());
 }
 private ExecutionResult blocked(UUID id,String code,String reason){return new ExecutionResult(id,null,null,code+": "+reason,null);}public record ExecutionResult(UUID applicationId,UUID attemptId,SubmissionAttemptState state,String blocker,String confirmationReference){}
}
