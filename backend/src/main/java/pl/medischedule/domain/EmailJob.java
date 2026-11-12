package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_jobs")
public class EmailJob extends BaseEntity {
  public String recipient;
  public String subject;

  @Column(columnDefinition = "text")
  public String body;

  public String status = "pending";
  public int attempts;
  public Instant nextAttemptAt = Instant.now();
  public Instant createdAt = Instant.now();
  public Instant sentAt;

  @Column(columnDefinition = "text")
  public String lastError;
}
