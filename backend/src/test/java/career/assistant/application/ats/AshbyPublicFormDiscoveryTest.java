package career.assistant.application.ats;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AshbyPublicFormDiscoveryTest {
    @Test
    void discoversZiinaPublicGraphqlFieldsAndHandlesFormIdentifiersWithoutReturningValues() {
        String posting = "b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb";
        AshbyPublicFormDiscovery.PublicFormTransport transport = new AshbyPublicFormDiscovery.PublicFormTransport() {
            public String get(String url) {
                return "{\"jobs\":[{\"id\":\"" + posting + "\",\"applyUrl\":\"https://jobs.ashbyhq.com/ziina/" + posting + "\"}]}";
            }
            public String post(String url, Object body) {
                return """
                        {"data":{"jobPosting":{"id":"b72caf1d-e9a0-483f-9fb9-4d1cd7f28ecb","applicationForm":{
                          "id":"opaque-form-id","sourceFormDefinitionId":"opaque-definition-id","sections":[
                            {"isHidden":false,"fieldEntries":[
                              {"id":"name-entry","isRequired":true,"field":{"path":"_systemfield_name","humanReadablePath":"Name","title":"Name","type":"String"}},
                              {"id":"email-entry","isRequired":true,"field":{"path":"_systemfield_email","title":"Email","type":"Email"}},
                              {"id":"resume-entry","isRequired":true,"field":{"path":"_systemfield_resume","title":"Resume","type":"File"}},
                              {"id":"phone-entry","isRequired":true,"field":{"path":"phone","title":"Phone","type":"String"}},
                              {"id":"cover-entry","isRequired":false,"field":{"path":"cover_letter","title":"Cover Letter","type":"File"}},
                              {"id":"hidden-entry","isRequired":true,"isHidden":true,"field":{"path":"secret","title":"Secret","type":"String"}}
                            ]}
                          ]}}}}}
                        """;
            }
        };
        AshbyPublicFormDiscovery discovery = new AshbyPublicFormDiscovery(transport, new ObjectMapper());

        AtsAdapter.FormDefinition form = discovery.discover("https://jobs.ashbyhq.com/ziina/" + posting);

        assertEquals(posting, form.postingId());
        assertEquals(5, form.fields().size());
        assertEquals(4, form.fields().stream().filter(AtsAdapter.FormField::required).count());
        assertTrue(form.formTokenPresent());
        assertEquals("File", form.fields().get(2).type());
        assertFalse(form.fields().stream().anyMatch(field -> field.path().equals("secret")));
    }
}
