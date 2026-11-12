package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt extends BaseEntity {
  @Column(nullable = false, length = 255)
  public String email;

  @Column(name = "ip_address", length = 45)
  public String ipAddress;

  @Column(name = "attempted_at", nullable = false)
  public Instant attemptedAt = Instant.now();

  @Column(nullable = false)
  public boolean success;
}
