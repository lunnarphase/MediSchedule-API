package pl.medischedule.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.medischedule.domain.Doctor;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
  Optional<Doctor> findByUserId(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Doctor d where d.id = :id")
  Optional<Doctor> lockById(@Param("id") Long id);
}
