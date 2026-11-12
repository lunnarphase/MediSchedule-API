package pl.medischedule.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.medischedule.domain.ScheduleTemplate;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Long> {
  List<ScheduleTemplate> findByDoctorIdOrderByNameAsc(Long doctorId);
}
