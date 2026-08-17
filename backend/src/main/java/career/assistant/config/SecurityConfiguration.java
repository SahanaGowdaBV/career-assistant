package career.assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfiguration {

    private final String issuer;
    private final String jwksUri;
    private final Set<String> allowedEmails;

    public SecurityConfiguration(
            @Value("${app.security.supabase-issuer:}") String issuer,
            @Value("${app.security.supabase-jwks-uri:}") String jwksUri,
            @Value("${app.security.allowed-emails:}") String allowedEmails
    ) {
        this.issuer = requiredAbsoluteUri(issuer, "SUPABASE_AUTH_ISSUER");
        this.jwksUri = requiredAbsoluteUri(jwksUri, "SUPABASE_AUTH_JWKS_URI");
        this.allowedEmails = Arrays.stream(allowedEmails.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (this.allowedEmails.isEmpty()) {
            throw new IllegalStateException("APP_ALLOWED_EMAILS must contain at least one email address");
        }
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ScraperIngestionTokenFilter ingestionTokenFilter
    ) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> allowedUser = (authentication, context) -> {
            if (!(authentication.get() instanceof JwtAuthenticationToken jwtAuthentication)
                    || !authentication.get().isAuthenticated()) {
                return new AuthorizationDecision(false);
            }
            Object emailClaim = jwtAuthentication.getToken().getClaim("email");
            return new AuthorizationDecision(emailClaim instanceof String email
                    && allowedEmails.contains(email.trim().toLowerCase(Locale.ROOT)));
        };

        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/scraper/ingest").permitAll()
                        .requestMatchers("/api/**").access(allowedUser)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .addFilterBefore(ingestionTokenFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    FilterRegistrationBean<ScraperIngestionTokenFilter> scraperIngestionFilterRegistration(
            ScraperIngestionTokenFilter filter
    ) {
        FilterRegistrationBean<ScraperIngestionTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static String requiredAbsoluteUri(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be configured");
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(environmentVariable + " must be a valid absolute URI", exception);
        }
        if (!uri.isAbsolute()) {
            throw new IllegalStateException(environmentVariable + " must be a valid absolute URI");
        }
        return uri.toString().replaceAll("/+$", "");
    }
}
