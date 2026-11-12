package pl.medischedule.security;

import java.util.Arrays;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pl.medischedule.domain.User;
import pl.medischedule.repository.UserRepository;
import pl.medischedule.web.ApiException;

@Component
public class CurrentUser {
  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public User user() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof SessionUser principal))
      throw new ApiException(401, "Zaloguj się, aby kontynuować.");
    User user =
        users.findById(principal.id()).orElseThrow(() -> new ApiException(401, "Sesja wygasła."));
    if (user.blocked) throw new ApiException(401, "Konto jest zablokowane.");
    return user;
  }

  public User require(String... roles) {
    User user = user();
    if (Arrays.stream(roles).noneMatch(role -> role.equals(user.role.name)))
      throw new ApiException(403, "Nie masz uprawnień do tej operacji.");
    return user;
  }
}
