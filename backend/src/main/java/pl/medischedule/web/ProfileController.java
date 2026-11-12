package pl.medischedule.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.medischedule.service.UserService;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
  private final UserService users;

  public ProfileController(UserService users) {
    this.users = users;
  }

  @GetMapping
  public UserService.UserView get() {
    return users.me();
  }

  @PutMapping
  public UserService.UserView update(@Valid @RequestBody UserService.ProfileRequest body) {
    return users.updateProfile(body);
  }

  @PutMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(@Valid @RequestBody UserService.PasswordRequest body) {
    users.changePassword(body);
  }
}
