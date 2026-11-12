package pl.medischedule.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.medischedule.domain.Availability;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
  List<Availability> findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(
      Long doctorId, LocalDate from, LocalDate to);
}
