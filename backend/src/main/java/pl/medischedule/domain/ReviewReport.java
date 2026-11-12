package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "review_reports")
public class ReviewReport extends BaseEntity {
  @ManyToOne
  @JoinColumn(name = "id_review", nullable = false)
  public Review review;

  @ManyToOne
  @JoinColumn(name = "id_reporter", nullable = false)
  public User reporter;

  public String category;

  @Column(columnDefinition = "text")
  public String reason;

  public String status = "pending";

  @Column(columnDefinition = "text")
  public String adminResponse;

  public Instant createdAt = Instant.now();
}
