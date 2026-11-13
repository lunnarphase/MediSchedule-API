package pl.medischedule.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.medischedule.service.ReviewService;

@RestController
@RequestMapping("/api")
public class ReviewController {
  private final ReviewService service;

  public ReviewController(ReviewService service) {
    this.service = service;
  }

  @GetMapping("/doctors/{id}/reviews")
  Object reviews(@PathVariable Long id) {
    return service.publicReviews(id);
  }

  @GetMapping("/doctor/reviews")
  Object mine() {
    return service.mine();
  }

  @PostMapping("/appointments/{id}/review")
  @ResponseStatus(HttpStatus.CREATED)
  Object review(@PathVariable Long id, @RequestBody ReviewBody body) {
    return service.submit(id, body.rating, body.comment);
  }

  @PostMapping("/reviews/{id}/reports")
  @ResponseStatus(HttpStatus.CREATED)
  Object report(@PathVariable Long id, @RequestBody Report body) {
    service.report(id, body.category, body.reason);
    return Map.of("message", "Zgłoszenie przekazane do moderacji.");
  }

  @GetMapping("/admin/reviews")
  Object all() {
    return service.all();
  }

  @DeleteMapping("/admin/reviews/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable Long id) {
    service.delete(id);
  }

  @GetMapping("/admin/reports")
  Object reports() {
    return service.reports();
  }

  @PatchMapping("/admin/reports/{id}")
  Object moderate(@PathVariable Long id, @RequestBody Moderation body) {
    return service.moderate(id, body.action, body.response);
  }

  public record ReviewBody(int rating, String comment) {}

  public record Report(String category, String reason) {}

  public record Moderation(String action, String response) {}
}
