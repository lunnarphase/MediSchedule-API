package pl.medischedule.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.medischedule.domain.EmailJob;

public interface EmailJobRepository extends JpaRepository<EmailJob, Long> {
  @Query(
      value =
          "SELECT * FROM email_jobs WHERE status IN ('pending','retry') AND next_attempt_at <="
              + " now() ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Optional<EmailJob> nextJob();
}
