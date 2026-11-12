package pl.medischedule.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.medischedule.domain.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Review r where r.id = :id")
  Optional<Review> lockById(@Param("id") Long id);

  List<Review> findByDoctorIdAndDeletedFalseOrderByCreatedAtDesc(Long doctorId);

  List<Review> findByDeletedFalseOrderByCreatedAtDesc();

  boolean existsByAppointmentId(Long appointmentId);
}
