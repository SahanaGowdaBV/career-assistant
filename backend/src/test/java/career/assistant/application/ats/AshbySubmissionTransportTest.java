package career.assistant.application.ats;
import org.junit.jupiter.api.*; import com.sun.net.httpserver.*; import java.net.*; import java.nio.charset.StandardCharsets; import java.util.*; import static org.junit.jupiter.api.Assertions.*;
class AshbySubmissionTransportTest { HttpServer server; int calls;
 @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("localhost",0),0);server.createContext("/submit",e->{calls++;byte[] b="{\"applicationId\":\"abc\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();}
 @AfterEach void stop(){server.stop(0);}
 @Test void dryRunMakesNoCallAndValidatesAttachments(){var p=plan("https://jobs.ashbyhq.com/submit");var r=new AshbySubmissionTransport().submit(p,true);assertEquals(AshbySubmissionResult.Outcome.FAILED_BEFORE_SUBMISSION,r.outcome());assertEquals(0,calls);}
 @Test void rejectsUnallowlistedAction(){var r=new AshbySubmissionTransport().submit(plan("https://evil.example/submit"),true);assertEquals(AshbySubmissionResult.Outcome.REVIEW_REQUIRED,r.outcome());}
 @Test void confirmsOnlyExplicitEvidence(){String action="http://localhost:"+server.getAddress().getPort()+"/submit";var r=new AshbySubmissionTransport().submit(plan(action),false);assertEquals(AshbySubmissionResult.Outcome.REVIEW_REQUIRED,r.outcome());assertEquals(0,calls);}
 private AshbySubmissionTransport.SubmissionPlan plan(String action){return new AshbySubmissionTransport.SubmissionPlan(action,"b",Map.of("name","Candidate"),List.of(new AshbySubmissionTransport.Attachment("resume","r.pdf","application/pdf",new byte[]{1,2})));}
}
