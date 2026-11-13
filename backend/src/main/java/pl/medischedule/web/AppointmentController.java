package pl.medischedule.web;

import java.time.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.service.AppointmentService;

@RestController
@RequestMapping("/api")
public class AppointmentController {
  private final AppointmentService service;
  private final CurrentUser current;

  public AppointmentController(AppointmentService service, CurrentUser current) {
    this.service = service;
    this.current = current;
  }

  @GetMapping("/appointments")
  Object list() {
    return service.list();
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  Object book(@RequestBody Booking body) {
    return service.book(body.doctorId, body.date, body.time);
  }

  @PatchMapping("/appointments/{id}/cancel")
  Object cancel(@PathVariable Long id, @RequestBody Cancel body) {
    return service.cancel(id, body.reason);
  }

  @PatchMapping("/appointments/{id}/status")
  Object status(@PathVariable Long id, @RequestBody Status body) {
    return service.status(id, body.status, body.recommendations);
  }

  @GetMapping("/admin/appointments")
  Object admin() {
    current.require("admin");
    return service.list();
  }

  @PutMapping("/admin/appointments/{id}")
  Object update(@PathVariable Long id, @RequestBody Edit body) {
    return service.adminUpdate(
        id, body.doctorId, body.patientId, body.date, body.time, body.status);
  }

  @DeleteMapping("/admin/appointments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable Long id) {
    service.adminDelete(id);
  }

  public record Booking(Long doctorId, LocalDate date, LocalTime time) {}

  public record Cancel(String reason) {}

  public record Status(String status, String recommendations) {}

  public record Edit(
      Long doctorId, Long patientId, LocalDate date, LocalTime time, String status) {}
}
