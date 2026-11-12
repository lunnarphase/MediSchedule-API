package pl.medischedule.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.medischedule.service.UserService;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUsersController {
  private final UserService users;

  public AdminUsersController(UserService users) {
    this.users = users;
  }

  public record BlockRequest(@NotNull Boolean blocked) {}

  @GetMapping
  public List<UserService.UserView> list() {
    return users.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserService.UserView add(@Valid @RequestBody UserService.AddUserRequest body) {
    return users.add(body);
  }

  @PutMapping("/{id}")
  public UserService.UserView edit(
      @PathVariable Long id, @Valid @RequestBody UserService.EditUserRequest body) {
    return users.edit(id, body);
  }

  @PatchMapping("/{id}/block")
  public UserService.UserView block(@PathVariable Long id, @Valid @RequestBody BlockRequest body) {
    return users.block(id, body.blocked());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    users.delete(id);
  }
}
