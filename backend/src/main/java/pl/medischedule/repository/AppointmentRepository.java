package pl.medischedule.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.medischedule.domain.Appointment;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
  List<Appointment> findByDoctorIdOrderByDateDescTimeDesc(Long doctorId);

  List<Appointment> findByPatientUserIdOrderByDateDescTimeDesc(Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Appointment a where a.id = :id")
  Optional<Appointment> lockById(@Param("id") Long id);

  @Query(
      "select a from Appointment a where a.doctor.id=:doctor and a.status <> 'cancelled' and"
          + " a.startsAt < :end and a.endsAt > :start")
  List<Appointment> overlaps(
      @Param("doctor") Long doctor,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
