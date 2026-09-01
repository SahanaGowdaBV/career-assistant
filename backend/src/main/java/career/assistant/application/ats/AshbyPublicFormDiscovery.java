package career.assistant.application.ats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AshbyPublicFormDiscovery {
    private static final String GRAPHQL_URL = "https://jobs.ashbyhq.com/api/non-user-graphql?op=ApiJobPosting";
    private static final String FORM_QUERY = """
            query ApiJobPosting($organizationHostedJobsPageName: String!, $jobPostingId: String!) {
              jobPosting(organizationHostedJobsPageName: $organizationHostedJobsPageName, jobPostingId: $jobPostingId) {
                id
                applicationForm {
                  id
                  sourceFormDefinitionId
                  sections {
                    isHidden
                    fieldEntries { id field isRequired isHidden }
                  }
                }
              }
            }
            """;

    private final PublicFormTransport transport;
    private final ObjectMapper objectMapper;

    @Autowired
    public AshbyPublicFormDiscovery(RestClient.Builder builder) {
        this(new PublicFormTransport() {
            private final RestClient client = builder.build();
            public String get(String url) { return client.get().uri(url).retrieve().body(String.class); }
            public String post(String url, Object body) {
                return client.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
            }
        }, new ObjectMapper());
    }

    AshbyPublicFormDiscovery(PublicFormTransport transport, ObjectMapper objectMapper) {
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    public AtsAdapter.FormDefinition discover(String postingUrl) {
        PublicAtsAdapters.AshbyPosting posting = PublicAtsAdapters.parseAshbyUrl(postingUrl)
                .orElseThrow(() -> new AtsDiscoveryException("Invalid Ashby public posting URL"));
        try {
            String boardJson = transport.get("https://api.ashbyhq.com/posting-api/job-board/" + posting.boardSlug());
            JsonNode job = findPosting(objectMapper.readTree(boardJson).path("jobs"), posting.postingId().toString());
            if (job == null) throw new AtsDiscoveryException("Ashby posting is not present on the public job board");

            Map<String, Object> request = Map.of(
                    "operationName", "ApiJobPosting",
                    "variables", Map.of(
                            "organizationHostedJobsPageName", posting.boardSlug(),
                            "jobPostingId", posting.postingId().toString()),
                    "query", FORM_QUERY);
            JsonNode response = objectMapper.readTree(transport.post(GRAPHQL_URL, request));
            if (response.hasNonNull("errors"))
                throw new AtsDiscoveryException("Ashby public form discovery returned an error");
            JsonNode form = response.path("data").path("jobPosting").path("applicationForm");
            if (form.isMissingNode() || form.isNull())
                throw new AtsDiscoveryException("Ashby public form definition was not discoverable");
            List<AtsAdapter.FormField> fields = extractFields(form);
            if (fields.isEmpty()) throw new AtsDiscoveryException("Ashby public form definition contained no visible fields");
            boolean tokenPresent = text(form, "id") != null && text(form, "sourceFormDefinitionId") != null;
            return new AtsAdapter.FormDefinition(posting.postingId().toString(), fields, tokenPresent);
        } catch (AtsDiscoveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AtsDiscoveryException("Ashby public form discovery failed without exposing response data", exception);
        }
    }

    private JsonNode findPosting(JsonNode jobs, String id) {
        if (!jobs.isArray()) return null;
        for (JsonNode job : jobs) if (id.equalsIgnoreCase(text(job, "id"))) return job;
        return null;
    }

    private List<AtsAdapter.FormField> extractFields(JsonNode form) {
        Map<String, AtsAdapter.FormField> fields = new LinkedHashMap<>();
        for (JsonNode section : form.path("sections")) {
            if (section.path("isHidden").asBoolean(false)) continue;
            for (JsonNode entry : section.path("fieldEntries")) {
                if (entry.path("isHidden").asBoolean(false)) continue;
                JsonNode field = entry.path("field");
                if (field.isTextual()) {
                    try { field = objectMapper.readTree(field.asText()); }
                    catch (Exception ignored) { continue; }
                }
                String path = text(field, "path");
                String title = firstText(field, "title", "humanReadablePath", "name", "label");
                String type = firstText(field, "type", "fieldType", "__autoSerializationID");
                if (path == null || title == null || type == null) continue;
                List<String> options = new ArrayList<>();
                JsonNode optionNodes = field.path("selectableValues");
                if (!optionNodes.isArray()) optionNodes = field.path("options");
                if (optionNodes.isArray()) for (JsonNode option : optionNodes) {
                    String value = option.isTextual() ? option.asText() : firstText(option, "label", "value", "title");
                    if (value != null) options.add(value);
                }
                fields.putIfAbsent(path, new AtsAdapter.FormField(path, title, type,
                        entry.path("isRequired").asBoolean(false), options));
            }
        }
        return List.copyOf(fields.values());
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) { String value = text(node, name); if (value != null) return value; }
        return null;
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node == null ? null : node.get(name);
        if (value == null || !value.isValueNode()) return null;
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }

    interface PublicFormTransport {
        String get(String url);
        String post(String url, Object body);
    }
}
