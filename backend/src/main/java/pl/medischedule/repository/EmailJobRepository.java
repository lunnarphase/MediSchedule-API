package pl.medischedule.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import pl.medischedule.domain.EmailJob;

public interface EmailJobRepository extends JpaRepository<EmailJob, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from EmailJob e where e.id = :id")
  Optional<EmailJob> lockById(Long id);

  @Query(
      value =
          """
          SELECT * FROM email_jobs
          WHERE (status = 'pending' AND next_attempt_at <= :now)
             OR (status = 'processing' AND lease_until <= :now)
          ORDER BY next_attempt_at, id
          LIMIT :limit FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<EmailJob> lockDue(Instant now, int limit);

  @Query(
      """
      select e from EmailJob e
      where (:status = '' or e.status = :status)
        and (:query = '' or locate(:query, lower(concat(e.recipient, ' ', e.subject))) > 0)
      """)
  Page<EmailJob> search(String status, String query, Pageable pageable);

  @Modifying
  @Query(
      """
      update EmailJob e set e.status = 'sent', e.sentAt = :now,
        e.leaseUntil = null, e.lastError = null
      where e.id = :id and e.status = 'processing' and e.attempts = :attempt
      """)
  int markSent(Long id, int attempt, Instant now);

  @Modifying
  @Query(
      """
      update EmailJob e set e.status = :status, e.nextAttemptAt = :next,
        e.leaseUntil = null, e.lastError = :error
      where e.id = :id and e.status = 'processing' and e.attempts = :attempt
      """)
  int markFailed(Long id, int attempt, String status, Instant next, String error);
}
