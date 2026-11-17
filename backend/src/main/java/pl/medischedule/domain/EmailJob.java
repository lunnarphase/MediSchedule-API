package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_jobs")
public class EmailJob extends BaseEntity {
  @ManyToOne
  @JoinColumn(name = "id_user", nullable = false)
  public User user;

  @ManyToOne
  @JoinColumn(name = "id_appointment")
  public Appointment appointment;

  public String recipient;
  public String subject;

  @Column(columnDefinition = "text")
  public String body;

  public String status = "pending";
  public int attempts;
  public Instant nextAttemptAt = Instant.now();
  public Instant leaseUntil;
  public Instant createdAt = Instant.now();
  public Instant sentAt;

  @Column(length = 1000)
  public String lastError;
}
