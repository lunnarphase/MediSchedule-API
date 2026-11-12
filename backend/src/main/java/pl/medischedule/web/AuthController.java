package pl.medischedule.web;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import pl.medischedule.service.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository contexts;
  private final SessionAuthenticationStrategy sessions;
  private final UserService users;
  private final LoginProtectionService protection;

  public AuthController(
      AuthenticationManager authenticationManager,
      SecurityContextRepository contexts,
      SessionAuthenticationStrategy sessions,
      UserService users,
      LoginProtectionService protection) {
    this.authenticationManager = authenticationManager;
    this.contexts = contexts;
    this.sessions = sessions;
    this.users = users;
    this.protection = protection;
  }

  public record LoginRequest(
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(max = 1024) String password) {}

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/login")
  public UserService.UserView login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    String email = UserService.normalizeEmail(body.email());
    String ip = request.getRemoteAddr();
    protection.check(email, ip);
    try {
      var auth =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(email, body.password()));
      sessions.onAuthentication(auth, request, response);
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
      contexts.saveContext(context, request, response);
      protection.record(email, ip, true);
      return users.me();
    } catch (AuthenticationException error) {
      protection.record(email, ip, false);
      throw new ApiException(401, "Nieprawidłowy e-mail lub hasło.");
    }
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserService.UserView register(@Valid @RequestBody UserService.RegisterRequest body) {
    return users.register(body);
  }

  @GetMapping("/me")
  public UserService.UserView me() {
    return users.me();
  }
}
