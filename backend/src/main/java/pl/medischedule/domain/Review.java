package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {
  @OneToOne
  @JoinColumn(name = "id_appointment", nullable = false, unique = true)
  public Appointment appointment;

  @ManyToOne
  @JoinColumn(name = "id_doctor", nullable = false)
  public Doctor doctor;

  @ManyToOne
  @JoinColumn(name = "id_patient", nullable = false)
  public Patient patient;

  public int rating;

  @Column(columnDefinition = "text")
  public String comment;

  public Instant createdAt = Instant.now();
  public boolean deleted;
}
