package pl.medischedule.domain;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "doctor_schedule_templates")
public class ScheduleTemplate extends BaseEntity {
  @ManyToOne
  @JoinColumn(name = "id_doctor", nullable = false)
  public Doctor doctor;

  public String name;
  public LocalTime startTime;
  public LocalTime endTime;
}
