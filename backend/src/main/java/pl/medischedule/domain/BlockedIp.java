package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "blocked_ips")
public class BlockedIp {
  @Id
  @Column(name = "ip_address", length = 45)
  public String ipAddress;

  @Column(name = "blocked_until")
  public Instant blockedUntil;

  @Column(columnDefinition = "text")
  public String reason;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();
}
