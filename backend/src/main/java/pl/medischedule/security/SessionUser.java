package pl.medischedule.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pl.medischedule.domain.User;

public record SessionUser(Long id, String email, String password, String role, boolean blocked)
    implements UserDetails, Serializable {
  public static SessionUser from(User user) {
    return new SessionUser(user.id, user.email, user.password, user.role.name, user.blocked);
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonLocked() {
    return !blocked;
  }

  @Override
  public boolean isEnabled() {
    return !blocked;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }
}
