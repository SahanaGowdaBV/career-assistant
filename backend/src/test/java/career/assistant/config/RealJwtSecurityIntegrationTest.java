package career.assistant.config;

import career.assistant.company.service.CompanyService;
import career.assistant.job.service.JobService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Exercises the production Nimbus decoder against a local ES256 JWKS endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
class RealJwtSecurityIntegrationTest {

    private static final String ISSUER = "https://local.supabase.test/auth/v1";
    private static final String EMAIL = "allowed@example.com";
    private static final ECKey SIGNING_KEY = generateKey();
    private static HttpServer jwksServer;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobs;

    @MockitoBean
    private CompanyService companies;

    @BeforeAll
    static void startJwksServer() throws IOException {
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", RealJwtSecurityIntegrationTest::writeJwks);
        jwksServer.start();
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        // The server is started before the context resolves the dynamic URI.
        if (jwksServer == null) {
            try {
                startJwksServer();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to start local JWKS server", exception);
            }
        }
        registry.add("app.security.supabase-issuer", () -> ISSUER);
        registry.add("app.security.supabase-jwks-uri",
                () -> "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks");
        registry.add("app.security.allowed-emails", () -> EMAIL);
    }

    @Test
    void realDecoderAcceptsValidEs256Jwt() throws Exception {
        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + token(ISSUER, EMAIL)))
                .andExpect(result -> {
                    assertNotEquals(401, result.getResponse().getStatus());
                    assertNotEquals(403, result.getResponse().getStatus());
                });
    }

    @Test
    void realDecoderRejectsWrongIssuer() throws Exception {
        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + token("https://wrong.example/auth/v1", EMAIL)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorizationRejectsDisallowedEmailAfterRealDecoding() throws Exception {
        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + token(ISSUER, "other@example.com")))
                .andExpect(status().isForbidden());
    }

    private static void writeJwks(HttpExchange exchange) throws IOException {
        ECKey publicKey = new ECKey.Builder(SIGNING_KEY.toPublicJWK())
                .algorithm(JWSAlgorithm.ES256)
                .build();
        byte[] body = JSONObjectUtils.toJSONString(new JWKSet(publicKey).toJSONObject())
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String token(String issuer, String email) throws JOSEException {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(SIGNING_KEY.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject("single-user")
                        .claim("email", email)
                        .issueTime(Date.from(now.minusSeconds(30)))
                        .expirationTime(Date.from(now.plusSeconds(300)))
                        .build());
        jwt.sign(new ECDSASigner(SIGNING_KEY));
        return jwt.serialize();
    }

    private static ECKey generateKey() {
        try {
            return new ECKeyGeneratorCompat().generate();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class ECKeyGeneratorCompat {
        ECKey generate() throws JOSEException {
            return new com.nimbusds.jose.jwk.gen.ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256)
                    .keyID("local-es256")
                    .generate();
        }
    }
}
