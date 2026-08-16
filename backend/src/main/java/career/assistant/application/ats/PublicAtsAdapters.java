package career.assistant.application.ats;
import career.assistant.application.entity.Application; import career.assistant.job.entity.Job; import org.springframework.stereotype.Component; import java.net.URI; import java.util.*; import java.util.regex.Pattern;
@Component public class PublicAtsAdapters {
 private final List<AtsAdapter> adapters=List.of(
  adapter("GREENHOUSE","boards.greenhouse.io",Pattern.compile("^/[^/]+/(?:jobs/)?\\d+/?$")),
  adapter("LEVER","jobs.lever.co",Pattern.compile("^/[^/]+/[0-9a-fA-F-]{16,}/?$")),
  adapter("WORKABLE","apply.workable.com",Pattern.compile("^/[^/]+/j/[A-Za-z0-9_-]+/?$")));
 public Optional<AtsAdapter> resolve(String url){return adapters.stream().filter(a->a.supports(url)).findFirst();}
 public List<String> supported(){return adapters.stream().map(AtsAdapter::name).toList();}
 private static AtsAdapter adapter(String name,String host,Pattern path){return new AtsAdapter(){public String name(){return name;}public boolean supports(String url){try{URI u=URI.create(url);return "https".equalsIgnoreCase(u.getScheme())&&host.equalsIgnoreCase(u.getHost())&&path.matcher(u.getPath()).matches();}catch(Exception e){return false;}}public AdapterResult dryRun(Job j, Application a){return AdapterResult.dry(name);}};}
 public static String unsupportedReason(String url){String host="unknown";try{host=URI.create(url).getHost();}catch(Exception ignored){}if(host==null)host="unknown";String h=host.toLowerCase(Locale.ROOT);if(h.contains("workday"))return "Workday requires portal-specific review";if(h.contains("oracle")||h.contains("taleo"))return "Oracle/Taleo requires portal-specific review";if(h.contains("linkedin")||h.contains("naukri"))return "Authenticated job portal requires manual review";return "Unsupported or non-public application form (authentication/CAPTCHA is never bypassed): "+host;}
}
