package career.assistant.document.parsing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class SkillCatalog {

    private static final Map<String, List<String>> SKILLS = new LinkedHashMap<>();

    static {
        add("AWS", "aws", "amazon web services");
        add("Azure", "azure", "microsoft azure");
        add("Google Cloud", "google cloud", "gcp");
        add("Kubernetes", "kubernetes", "k8s");
        add("Docker", "docker");
        add("Terraform", "terraform");
        add("Ansible", "ansible");
        add("Helm", "helm");
        add("Jenkins", "jenkins");
        add("GitHub Actions", "github actions");
        add("GitLab CI", "gitlab ci", "gitlab-ci");
        add("CI/CD", "ci/cd", "continuous integration", "continuous delivery", "continuous deployment");
        add("Linux", "linux");
        add("Python", "python");
        add("Java", "java");
        add("JavaScript", "javascript");
        add("TypeScript", "typescript");
        add("Spring Boot", "spring boot");
        add("React", "react");
        add("Next.js", "next.js", "nextjs");
        add("PostgreSQL", "postgresql", "postgres");
        add("MySQL", "mysql");
        add("Redis", "redis");
        add("Kafka", "kafka");
        add("Prometheus", "prometheus");
        add("Grafana", "grafana");
        add("Datadog", "datadog");
        add("Amazon CloudWatch", "amazon cloudwatch", "cloudwatch");
        add("Splunk", "splunk");
        add("ELK", "elk", "elasticsearch", "logstash", "kibana");
        add("OpenTelemetry", "opentelemetry");
        add("Argo CD", "argo cd", "argocd");
        add("Git", "git");
        add("Bash", "bash", "shell scripting");
        add("PowerShell", "powershell");
        add("Networking", "networking");
        add("DevOps", "devops");
        add("SRE", "sre", "site reliability engineering");
    }

    private SkillCatalog() {
    }

    public static List<String> findMentionedSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        SKILLS.forEach((canonical, aliases) -> {
            if (aliases.stream().anyMatch(alias -> containsPhrase(lower, alias))) {
                found.add(canonical);
            }
        });
        return List.copyOf(found);
    }

    public static boolean containsSkill(String text, String skill) {
        return findMentionedSkills(text).stream().anyMatch(value -> value.equalsIgnoreCase(skill));
    }

    private static boolean containsPhrase(String lowerText, String alias) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])")
                .matcher(lowerText)
                .find();
    }

    private static void add(String canonical, String... aliases) {
        SKILLS.put(canonical, List.of(aliases));
    }
}
