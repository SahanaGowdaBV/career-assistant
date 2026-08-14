package career.assistant;

import career.assistant.scraper.config.JobSearchConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JobSearchConfig.class)
public class CareerAssistantApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerAssistantApiApplication.class, args);
    }
}