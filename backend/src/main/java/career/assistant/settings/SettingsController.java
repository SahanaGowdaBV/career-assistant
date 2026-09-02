package career.assistant.settings;

import career.assistant.settings.entity.UserSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController @RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsRepository repo;
    public SettingsController(SettingsRepository r){repo=r;}
    @GetMapping public UserSettings get(){return repo.findByOwnerSubject(subject()).orElseGet(this::defaults);}
    @GetMapping("/completeness") public ProfileCompleteness completeness(){UserSettings s=get();return new ProfileCompleteness(complete(s), missing(s));}
    @PutMapping public UserSettings save(@RequestBody UserSettings s){s.setId(id());s.setOwnerSubject(subject());s.setDryRun(true);return repo.save(s);}
    private UUID id(){return UUID.nameUUIDFromBytes(("applicant-profile:"+subject()).getBytes(StandardCharsets.UTF_8));}
    private String subject(){var a=SecurityContextHolder.getContext().getAuthentication();if(a instanceof JwtAuthenticationToken jwt)return jwt.getToken().getSubject();throw new IllegalStateException("Authenticated user subject is required");}
    private UserSettings defaults(){UserSettings s=new UserSettings();s.setId(id());s.setOwnerSubject(subject());s.setProfileName("UAE DevOps Search");s.setLocations("Dubai,Abu Dhabi,Sharjah,UAE Remote");s.setRoles("DevOps Engineer,Senior DevOps Engineer,SRE,Cloud Engineer,Cloud Architect,Platform Engineer");s.setSkills("AWS,Kubernetes,Terraform,Docker,GitHub Actions,Helm,Linux,CI/CD,Grafana,Monitoring");s.setExperienceMin(4);s.setExperienceMax(8);return s;}
    private java.util.List<String> missing(UserSettings s){return java.util.stream.Stream.of(s.getLegalName(),s.getApplicationEmail(),s.getApplicationPhone(),s.getCurrentLocation(),s.getRelocation(),s.getVisaAnswer(),s.getSponsorshipAnswer(),s.getNoticePeriodDays()==null?null:String.valueOf(s.getNoticePeriodDays()),s.getLinkedinUrl(),s.getConsentAnswers()).filter(v->v==null||v.isBlank()).toList();}
    private boolean complete(UserSettings s){return missing(s).isEmpty();}
    public record ProfileCompleteness(boolean complete, java.util.List<String> missingFields){}
}
