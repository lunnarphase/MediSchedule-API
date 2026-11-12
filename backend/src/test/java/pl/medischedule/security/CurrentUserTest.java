package pl.medischedule.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.medischedule.domain.*;
import pl.medischedule.repository.UserRepository;
import pl.medischedule.web.ApiException;

class CurrentUserTest {
  UserRepository users = mock(UserRepository.class);
  CurrentUser current = new CurrentUser(users);

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doesNotTrustRoleCachedInSession() {
    User user = new User();
    user.id = 1L;
    user.role = new Role();
    user.role.name = "patient";
    when(users.findById(1L)).thenReturn(Optional.of(user));
    authenticate(new SessionUser(1L, "user@example.com", "", "admin", false));
    assertThrows(ApiException.class, () -> current.require("admin"));
    assertSame(user, current.require("patient"));
  }

  @Test
  void blockedAccountCannotUseExistingSession() {
    User user = new User();
    user.id = 1L;
    user.blocked = true;
    when(users.findById(1L)).thenReturn(Optional.of(user));
    authenticate(new SessionUser(1L, "user@example.com", "", "patient", false));
    assertThrows(ApiException.class, current::user);
  }

  private void authenticate(SessionUser user) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
  }
}
