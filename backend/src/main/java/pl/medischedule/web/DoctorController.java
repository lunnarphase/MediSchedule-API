package pl.medischedule.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
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

  @GetMapping("/doctor/profile")
  Object profile() {
    return service.profile();
  }

  @PutMapping("/doctor/profile")
  Object profile(@Valid @RequestBody Profile body) {
    return service.saveProfile(body.bio, body.visitPrice, body.visitDuration);
  }

  public record Profile(
      @NotNull String bio, @NotNull BigDecimal visitPrice, @Min(10) @Max(240) int visitDuration) {}
}
