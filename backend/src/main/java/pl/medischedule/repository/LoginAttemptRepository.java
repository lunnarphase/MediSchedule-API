package pl.medischedule.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.medischedule.domain.LoginAttempt;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
  List<LoginAttempt> findByAttemptedAtAfterOrderByAttemptedAtDesc(Instant since);

  List<LoginAttempt> findTop200ByOrderByAttemptedAtDesc();

  List<LoginAttempt> findByEmailIgnoreCase(String email);
}
