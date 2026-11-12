package pl.medischedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.medischedule.domain.Specialization;

public interface SpecializationRepository extends JpaRepository<Specialization, Long> {}
