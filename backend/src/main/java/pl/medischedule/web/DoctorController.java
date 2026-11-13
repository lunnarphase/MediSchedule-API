package pl.medischedule.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.medischedule.service.*;

@RestController
@RequestMapping("/api")
public class DoctorController {
  private final DoctorService service;
  private final Views views;

  public DoctorController(DoctorService service, Views views) {
    this.service = service;
    this.views = views;
  }

  @GetMapping("/specializations")
  Object specializations() {
    return service.specializations();
  }

  @GetMapping("/doctors")
  Object list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Long specialization) {
    return service.search(q, specialization);
  }

  @GetMapping("/doctors/{id}")
  Object doctor(@PathVariable Long id) {
    return views.doctor(service.get(id));
  }

  @GetMapping("/doctors/{id}/slots")
  Object slots(@PathVariable Long id, @RequestParam LocalDate date) {
    return service.slots(id, date);
  }

  @GetMapping("/doctors/{id}/available-dates")
  Object dates(@PathVariable Long id, @RequestParam LocalDate from, @RequestParam LocalDate to) {
    return service.availableDates(id, from, to);
  }

  @GetMapping("/doctor/availability")
  Object week(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return service.week(from, to);
  }

  @PutMapping("/doctor/availability")
  Object save(@RequestBody Week body) {
    return service.saveWeek(body.from, body.to, body.ranges);
  }

  @GetMapping("/doctor/templates")
  Object templates() {
    return service.templates();
  }

  @PostMapping("/doctor/templates")
  @ResponseStatus(HttpStatus.CREATED)
  Object template(@RequestBody Template body) {
    return service.saveTemplate(body.name, body.startTime, body.endTime);
  }

  @DeleteMapping("/doctor/templates/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable Long id) {
    service.deleteTemplate(id);
  }

  @GetMapping("/doctor/profile")
  Object profile() {
    return service.profile();
  }

  @PutMapping("/doctor/profile")
  Object profile(@Valid @RequestBody Profile body) {
    return service.saveProfile(body.bio, body.visitPrice, body.visitDuration);
  }

  public record Week(LocalDate from, LocalDate to, List<DoctorService.Range> ranges) {}

  public record Template(String name, LocalTime startTime, LocalTime endTime) {}

  public record Profile(
      @NotNull String bio, @NotNull BigDecimal visitPrice, @Min(10) @Max(240) int visitDuration) {}
}
