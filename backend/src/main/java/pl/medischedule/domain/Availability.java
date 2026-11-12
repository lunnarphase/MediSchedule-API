package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "doctor_availability")
public class Availability extends BaseEntity {
  @ManyToOne
  @JoinColumn(name = "id_doctor", nullable = false)
  public Doctor doctor;

  public LocalDate date;
  public LocalTime startTime;
  public LocalTime endTime;
}
