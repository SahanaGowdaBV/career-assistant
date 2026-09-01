package career.assistant.application.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PublicAtsAdaptersTest {
    private final PublicAtsAdapters adapters = new PublicAtsAdapters(
            mock(AshbyPublicFormDiscovery.class), new AshbyResponseClassifier(new ObjectMapper()));

    @Test void recognizesOnlyExactPublicFlows() {
        assertEquals("GREENHOUSE", adapters.resolve("https://boards.greenhouse.io/acme/jobs/12345").orElseThrow().name());
        assertEquals("LEVER", adapters.resolve("https://jobs.lever.co/acme/12345678-abcd-1234-abcd-123456789abc").orElseThrow().name());
        assertEquals("WORKABLE", adapters.resolve("https://apply.workable.com/acme/j/ABC123/").orElseThrow().name());
        assertTrue(adapters.resolve("https://evil.example/boards.greenhouse.io/acme/jobs/123").isEmpty());
    }

    @Test void recognizesAndParsesOnlyGenuineAshbyPostingUrls() {
        String url = "https://jobs.ashbyhq.com/ziina/b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb";
        assertEquals("ASHBY", adapters.resolve(url).orElseThrow().name());
        var parsed = PublicAtsAdapters.parseAshbyUrl(url).orElseThrow();
        assertEquals("ziina", parsed.boardSlug());
        assertEquals(UUID.fromString("b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb"), parsed.postingId());
        assertTrue(adapters.resolve("https://jobs.ashbyhq.com/ziina/not-a-posting").isEmpty());
        assertTrue(adapters.resolve("https://evil.example/jobs.ashbyhq.com/ziina/b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb").isEmpty());
    }

    @Test void ashbyReportsMandatorySystemFieldsWithoutPersonalValues() {
        var adapter = adapters.resolve("https://jobs.ashbyhq.com/ziina/b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb").orElseThrow();
        var fields = adapter.requiredFields(null);
        assertEquals(List.of("Name", "Email", "Resume", "Phone"),
                fields.stream().filter(AtsAdapter.FormField::required).map(AtsAdapter.FormField::name).toList());
        assertTrue(fields.stream().allMatch(field -> field.path().startsWith("_systemfield_")));
        var confirmation = adapter.classifyResponse(200, "{\"applicationId\":\"confirmation-123\"}");
        assertEquals(AtsAdapter.Outcome.CONFIRMED, confirmation.outcome());
        assertEquals("confirmation-123", confirmation.confirmationId());
    }

    @Test void unsupportedAndAuthenticatedFlowsAreManual() {
        assertTrue(PublicAtsAdapters.unsupportedReason("https://linkedin.com/jobs/1").contains("Authenticated"));
        assertTrue(PublicAtsAdapters.unsupportedReason("https://acme.myworkdayjobs.com/job/1").contains("Workday"));
    }
}
