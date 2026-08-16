package career.assistant.config;

import career.assistant.company.service.CompanyService;
import career.assistant.job.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTest {

    private static final String ISSUER = "https://test-project.supabase.co/auth/v1";
    private static final String INGESTION_TOKEN = "test-ingestion-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JobService jobs;

    @MockitoBean
    private CompanyService companies;

    @BeforeEach
    void configureDecoder() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return switch (token) {
                case "allowed-token" -> jwt(token, "Allowed@Example.com");
                case "disallowed-token" -> jwt(token, "other@example.com");
                case "missing-email-token" -> jwt(token, null);
                default -> throw new BadJwtException("Invalid test token");
            };
        });
    }

    @Test
    void apiAndActuatorHealthArePublicButSensitiveActuatorEndpointsAreNot() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiRejectsMissingAndInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApiRejectsDisallowedOrMissingJwtEmailClaim() throws Exception {
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer disallowed-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer missing-email-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedApiAllowsConfiguredJwtEmailClaimCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/settings")
                        .header("Authorization", "Bearer allowed-token"))
                .andExpect(status().isOk());
    }

    @Test
    void scraperIngestionRequiresItsOwnTokenAndDoesNotRequireUserJwt() throws Exception {
        mockMvc.perform(post("/api/scraper/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ingestionPayload()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/scraper/ingest")
                        .header(ScraperIngestionTokenFilter.TOKEN_HEADER, "wrong-token")
                        .header("Authorization", "Bearer allowed-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ingestionPayload()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/scraper/ingest")
                        .header(ScraperIngestionTokenFilter.TOKEN_HEADER, INGESTION_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ingestionPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.accepted").value(1));
    }

    @Test
    void missingAllowedEmailConfigurationFailsClosed() {
        assertThrows(IllegalStateException.class, () -> new SecurityConfiguration(
                ISSUER,
                ISSUER + "/.well-known/jwks.json",
                "  "
        ));
    }

    private Jwt jwt(String token, String email) {
        Jwt.Builder builder = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject("single-user")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300));
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.build();
    }

    private String ingestionPayload() {
        return """
                {
                  "dryRun": true,
                  "jobs": [{
                    "title": "Platform Engineer",
                    "company": "Example",
                    "location": "Dubai, UAE",
                    "experienceMin": 4,
                    "experienceMax": 8,
                    "experienceUnknown": false,
                    "source": "COMPANY_CAREER_PAGE",
                    "sourceId": "security-test-1",
                    "url": "https://example.com/jobs/security-test-1"
                  }]
                }
                """;
    }
}
