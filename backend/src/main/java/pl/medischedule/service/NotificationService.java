package pl.medischedule.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.*;

@Service
@Transactional
public class NotificationService {
  private final NotificationRepository notifications;
  private final UserRepository users;
  private final CurrentUser current;
  private final Views views;

  public NotificationService(
      NotificationRepository notifications,
      UserRepository users,
      CurrentUser current,
      Views views) {
    this.notifications = notifications;
    this.users = users;
    this.current = current;
    this.views = views;
  }

  public void add(User user, String message, String type, Long relatedId) {
    Notification n = new Notification();
    n.user = user;
    n.message = message;
    n.type = type;
    n.relatedId = relatedId;
    notifications.save(n);
  }

  public void notifyAdmins(String message, String type) {
    users.findAll().stream()
        .filter(u -> u.role.name.equals("admin"))
        .forEach(u -> add(u, message, type, null));
  }

  public List<Map<String, Object>> list() {
    return notifications.findTop50ByUserIdOrderByCreatedAtDesc(current.user().id).stream()
        .map(views::notification)
        .toList();
  }

  public void markRead() {
    notifications.findByUserId(current.user().id).forEach(n -> n.read = true);
  }

  public void deleteAll() {
    notifications.deleteAll(notifications.findByUserId(current.user().id));
  }

  public void delete(Long id) {
    var n =
        notifications
            .findById(id)
            .orElseThrow(() -> new ApiException(404, "Nie znaleziono powiadomienia."));
    if (!n.user.id.equals(current.user().id))
      throw new ApiException(403, "Brak dostępu do powiadomienia.");
    notifications.delete(n);
  }
}
