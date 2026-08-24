package career.assistant.application.ats;
import career.assistant.application.entity.Application; import career.assistant.job.entity.Job; import org.springframework.stereotype.Component; import java.net.URI; import java.util.*; import java.util.regex.Pattern;
@Component public class PublicAtsAdapters {
 private final List<AtsAdapter> adapters=List.of(
  adapter("GREENHOUSE","boards.greenhouse.io",Pattern.compile("^/[^/]+/(?:jobs/)?\\d+/?$")),
  adapter("LEVER","jobs.lever.co",Pattern.compile("^/[^/]+/[0-9a-fA-F-]{16,}/?$")),
  adapter("WORKABLE","apply.workable.com",Pattern.compile("^/[^/]+/j/[A-Za-z0-9_-]+/?$")),
  ashby());
 public Optional<AtsAdapter> resolve(String url){return adapters.stream().filter(a->a.supports(url)).findFirst();}
 public List<String> supported(){return adapters.stream().map(AtsAdapter::name).toList();}
 private static AtsAdapter adapter(String name,String host,Pattern path){return new AtsAdapter(){public String name(){return name;}public boolean supports(String url){try{URI u=URI.create(url);return "https".equalsIgnoreCase(u.getScheme())&&host.equalsIgnoreCase(u.getHost())&&path.matcher(u.getPath()).matches();}catch(Exception e){return false;}}public AdapterResult dryRun(Job j, Application a){return AdapterResult.dry(name);}};}
 private static AtsAdapter ashby(){return new AtsAdapter(){public String name(){return "ASHBY";}public boolean supports(String url){return parseAshbyUrl(url).isPresent();}public List<FormField> requiredFields(Job job){return List.of(new FormField("Name",true),new FormField("Email",true),new FormField("Resume",true),new FormField("Phone",true));}public AdapterResult dryRun(Job j,Application a){return AdapterResult.dry(name());}};}
 public static Optional<AshbyPosting> parseAshbyUrl(String url){try{URI u=URI.create(url);if(!"https".equalsIgnoreCase(u.getScheme())||!"jobs.ashbyhq.com".equalsIgnoreCase(u.getHost()))return Optional.empty();String[] p=u.getPath().split("/");if(p.length!=3||!p[1].matches("[A-Za-z0-9_-]+")||!p[2].matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))return Optional.empty();return Optional.of(new AshbyPosting(p[1],UUID.fromString(p[2])));}catch(Exception e){return Optional.empty();}}
 public record AshbyPosting(String boardSlug,UUID postingId){}
 public static String unsupportedReason(String url){String host="unknown";try{host=URI.create(url).getHost();}catch(Exception ignored){}if(host==null)host="unknown";String h=host.toLowerCase(Locale.ROOT);if(h.contains("workday"))return "Workday requires portal-specific review";if(h.contains("oracle")||h.contains("taleo"))return "Oracle/Taleo requires portal-specific review";if(h.contains("linkedin")||h.contains("naukri"))return "Authenticated job portal requires manual review";return "Unsupported or non-public application form (authentication/CAPTCHA is never bypassed): "+host;}
}
