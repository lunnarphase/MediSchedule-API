package pl.medischedule.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.medischedule.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  Object list() {
    return service.list();
  }

  @PatchMapping("/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void read() {
    service.markRead();
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void clear() {
    service.deleteAll();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
