package career.assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebCorsConfiguration(
            @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000}") String allowedOrigins
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);

        if (this.allowedOrigins.length == 0
                || Arrays.asList(this.allowedOrigins).contains("*")) {
            throw new IllegalArgumentException(
                    "CORS_ALLOWED_ORIGINS must contain explicit origins"
            );
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
