package career.assistant.config;

import career.assistant.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import(WebCorsConfiguration.class)
class WebCorsConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void permitsLocalFrontendPreflightWithRequiredMethodAndHeaders() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header(
                                "Access-Control-Request-Headers",
                                "content-type,authorization"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:3000"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        containsString("PATCH")
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        containsString("authorization")
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Credentials",
                        "true"
                ));
    }
}
