package pl.medischedule.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.medischedule.domain.ReviewReport;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {
  List<ReviewReport> findByReviewId(Long reviewId);

  List<ReviewReport> findByOrderByCreatedAtDesc();

  boolean existsByReviewIdAndReporterIdAndStatus(Long reviewId, Long reporterId, String status);
}
