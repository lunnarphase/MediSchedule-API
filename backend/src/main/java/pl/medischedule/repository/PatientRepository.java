package pl.medischedule.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.medischedule.domain.Patient;

public interface PatientRepository extends JpaRepository<Patient, Long> {
  Optional<Patient> findByUserId(Long userId);

  boolean existsByPesel(String pesel);
}
