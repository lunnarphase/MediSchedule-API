package pl.medischedule.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.medischedule.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

  List<Notification> findByUserId(Long userId);
}
