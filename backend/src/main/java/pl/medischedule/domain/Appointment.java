package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointments")
public class Appointment extends BaseEntity {
  @ManyToOne
  @JoinColumn(name = "id_patient", nullable = false)
  public Patient patient;

  @ManyToOne
  @JoinColumn(name = "id_doctor", nullable = false)
  public Doctor doctor;

  @Column(name = "appointment_date", nullable = false)
  public LocalDate date;

  @Column(name = "appointment_time", nullable = false)
  public LocalTime time;

  public LocalDateTime startsAt;
  public LocalDateTime endsAt;
  public String status = "confirmed";

  @Column(columnDefinition = "text")
  public String recommendations;

  @Column(columnDefinition = "text")
  public String cancelReason;

  @Column(columnDefinition = "text")
  public String cancelComment;

  public boolean reviewSubmitted;
}
