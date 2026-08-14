package career.assistant.scraper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "career-assistant.jobs")
public class JobSearchConfig {

    private int minimumExperience = 4;
    private int maximumExperience = 9;

    private List<String> locations = new ArrayList<>(
            List.of(
                    "Dubai",
                    "Abu Dhabi",
                    "Sharjah",
                    "United Arab Emirates",
                    "UAE"
            )
    );

    private List<String> jobTitles = new ArrayList<>(
            List.of(
                    "DevOps Engineer",
                    "Senior DevOps Engineer",
                    "Cloud Engineer",
                    "Cloud DevOps Engineer",
                    "Site Reliability Engineer",
                    "SRE",
                    "Platform Engineer",
                    "DevOps Architect",
                    "Cloud Architect"
            )
    );

    private List<String> keywords = new ArrayList<>(
            List.of(
                    "AWS",
                    "Azure",
                    "Kubernetes",
                    "Docker",
                    "Terraform",
                    "GitHub Actions",
                    "CI/CD",
                    "Jenkins",
                    "Linux"
            )
    );

    public int getMinimumExperience() {
        return minimumExperience;
    }

    public void setMinimumExperience(int minimumExperience) {
        this.minimumExperience = minimumExperience;
    }

    public int getMaximumExperience() {
        return maximumExperience;
    }

    public void setMaximumExperience(int maximumExperience) {
        this.maximumExperience = maximumExperience;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public List<String> getJobTitles() {
        return jobTitles;
    }

    public void setJobTitles(List<String> jobTitles) {
        this.jobTitles = jobTitles;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
}