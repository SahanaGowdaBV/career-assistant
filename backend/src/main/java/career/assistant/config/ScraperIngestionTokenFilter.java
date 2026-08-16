package career.assistant.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class ScraperIngestionTokenFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Scraper-Ingestion-Token";
    private static final String INGESTION_PATH = "/api/scraper/ingest";

    private final byte[] expectedTokenDigest;

    public ScraperIngestionTokenFilter(
            @Value("${app.security.scraper-ingestion-token:}") String expectedToken
    ) {
        this.expectedTokenDigest = expectedToken == null || expectedToken.isBlank()
                ? null
                : digest(expectedToken);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !INGESTION_PATH.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedToken = request.getHeader(TOKEN_HEADER);
        boolean accepted = expectedTokenDigest != null
                && suppliedToken != null
                && MessageDigest.isEqual(expectedTokenDigest, digest(suppliedToken));
        if (!accepted) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Invalid ingestion credentials\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
