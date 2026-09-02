package career.assistant.application.service;

import career.assistant.application.audit.*;
import career.assistant.application.entity.*;
import career.assistant.application.repository.ApplicationRepository;
import career.assistant.job.entity.Job;
import career.assistant.jobscore.repository.JobScoreRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnershipSecurityIntegrationTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void manualWorkerScopesSelectionToJwtOwner(){
  authenticate("user-a");var apps=mock(ApplicationRepository.class);when(apps.findByStatusAndOwnerSubject(ApplicationStatus.READY_TO_APPLY,"user-a")).thenReturn(List.of());
  var worker=new AutoApplyWorkerService(apps,mock(JobScoreRepository.class),mock(AutoApplyExecutionService.class),mock(AutoApplyEligibilityService.class),true,false,false,BigDecimal.valueOf(75));
  assertEquals(AutoApplyWorkerService.WorkerOutcome.NO_ELIGIBLE_APPLICATION,worker.run(true).outcome());verify(apps).findByStatusAndOwnerSubject(ApplicationStatus.READY_TO_APPLY,"user-a");verify(apps,never()).findByStatusAndOwnerSubjectIsNotNull(any());
 }
 @Test void schedulerExcludesOwnerNullLegacyRows(){
  var apps=mock(ApplicationRepository.class);var legacy=new Application();legacy.setStatus(ApplicationStatus.READY_TO_APPLY);when(apps.findByStatusAndOwnerSubjectIsNotNull(ApplicationStatus.READY_TO_APPLY)).thenReturn(List.of(legacy));var execution=mock(AutoApplyExecutionService.class);
  var worker=new AutoApplyWorkerService(apps,mock(JobScoreRepository.class),execution,mock(AutoApplyEligibilityService.class),true,false,true,BigDecimal.valueOf(75));
  assertEquals(AutoApplyWorkerService.WorkerOutcome.NO_ELIGIBLE_APPLICATION,worker.runScheduled().outcome());verifyNoInteractions(execution);
 }
 @Test void claimAndFinalizationRejectMismatchedOwners(){
  UUID appId=UUID.randomUUID(),attemptId=UUID.randomUUID();var repo=mock(SubmissionAttemptRepository.class);var apps=mock(ApplicationRepository.class);when(apps.findByIdAndOwnerSubject(appId,"user-b")).thenReturn(Optional.empty());var service=new SubmissionAttemptService(repo,apps);
  assertThrows(SubmissionAttemptService.SubmissionAttemptConflictException.class,()->service.claim(appId,"user-b","ASHBY","source","key","request"));
  when(repo.findByIdAndOwnerSubject(attemptId,"user-b")).thenReturn(Optional.empty());assertThrows(SubmissionAttemptService.SubmissionAttemptConflictException.class,()->service.finish(attemptId,"user-b",SubmissionAttemptState.FAILED,"validation",null,null));
 }
 @Test void ownedClaimUsesApplicationAndAttemptOwnerScope(){
  UUID appId=UUID.randomUUID();var repo=mock(SubmissionAttemptRepository.class);var apps=mock(ApplicationRepository.class);var app=new Application();app.setOwnerSubject("user-a");when(apps.findByIdAndOwnerSubject(appId,"user-a")).thenReturn(Optional.of(app));when(repo.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
  new SubmissionAttemptService(repo,apps).claim(appId,"user-a","ASHBY","source","key","request");verify(apps).findByIdAndOwnerSubject(appId,"user-a");verify(repo).findFirstBySourceFingerprintAndOwnerSubjectAndStateIn(anyString(),eq("user-a"),any());
 }
 private void authenticate(String subject){Jwt jwt=Jwt.withTokenValue("synthetic").header("alg","none").subject(subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));}
}
