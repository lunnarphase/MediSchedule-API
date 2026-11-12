package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {
  @ManyToOne
  @JoinColumn(name = "id_user", nullable = false)
  public User user;

  @Column(columnDefinition = "text")
  public String message;

  @Column(name = "is_read")
  public boolean read;

  public String type = "general";
  public Long relatedId;
  public Instant createdAt = Instant.now();
}
