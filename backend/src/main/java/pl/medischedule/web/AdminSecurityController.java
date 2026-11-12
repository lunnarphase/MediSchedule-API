package pl.medischedule.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.service.LoginProtectionService;

@RestController
@RequestMapping("/api/admin/security")
public class AdminSecurityController {
  private final CurrentUser current;
  private final LoginProtectionService protection;

  public AdminSecurityController(CurrentUser current, LoginProtectionService protection) {
    this.current = current;
    this.protection = protection;
  }

  @GetMapping
  public Map<String, Object> overview() {
    current.require("admin");
    return protection.overview();
  }

  @GetMapping("/attacks")
  public Map<String, Object> details(
      @RequestParam(required = false) String email, @RequestParam(required = false) String ip) {
    current.require("admin");
    return protection.details(email, ip);
  }

  @DeleteMapping("/blocks/{ip}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unblock(@PathVariable String ip) {
    current.require("admin");
    protection.unblock(ip);
  }
}
