package career.assistant.security;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
public final class AuthenticatedOwner {
 private AuthenticatedOwner() {}
 public static String required(){var a=SecurityContextHolder.getContext().getAuthentication(); if(a instanceof JwtAuthenticationToken j && j.getToken().getSubject()!=null && !j.getToken().getSubject().isBlank()) return j.getToken().getSubject(); throw new SecurityException("Authenticated user subject is required");}
 public static void verify(String owner){var a=SecurityContextHolder.getContext().getAuthentication(); if(a==null) return; if(!(a instanceof JwtAuthenticationToken) || owner==null || !owner.equals(required())) throw new SecurityException("Access denied");}
}
