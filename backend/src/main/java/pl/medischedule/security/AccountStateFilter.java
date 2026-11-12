package pl.medischedule.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.medischedule.repository.UserRepository;

public class AccountStateFilter extends OncePerRequestFilter {
  private final UserRepository users;

  public AccountStateFilter(UserRepository users) {
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof SessionUser principal) {
      boolean invalid = users.findById(principal.id()).map(user -> user.blocked).orElse(true);
      if (invalid) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
      }
    }
    chain.doFilter(request, response);
  }
}
