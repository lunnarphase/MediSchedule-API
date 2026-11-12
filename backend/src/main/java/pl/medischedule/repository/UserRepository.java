package pl.medischedule.repository;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import pl.medischedule.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmailIgnoreCase(String email);

  long countByRoleNameAndBlockedFalse(String name);

  List<User> findAllByOrderByIdAsc();
}
