package pl.medischedule.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.medischedule.service.EmailQueueService;

@RestController
@RequestMapping("/api/admin/emails")
@Tag(name = "E-maile")
public class AdminEmailController {
  private final EmailQueueService emails;

  public AdminEmailController(EmailQueueService emails) {
    this.emails = emails;
  }

  @GetMapping
  @Operation(
      summary = "Stronicowana historia wysyłki",
      description =
          "Tylko administrator. page od 0, size od 1 do 100; status: pending, processing, sent,"
              + " failed. q wyszukuje odbiorcę lub temat. Sortowanie: createdAt DESC, id DESC.")
  public PageResult<EmailQueueService.EmailView> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String q) {
    return emails.list(page, size, status, q);
  }

  @PostMapping("/{id}/retry")
  @Operation(
      summary = "Ponowienie nieudanej wysyłki",
      description =
          "Tylko administrator; wyłącznie status failed. Resetuje licznik prób i ponownie dodaje"
              + " zadanie do kolejki.")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void retry(@PathVariable Long id) {
    emails.retry(id);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Szczegóły wiadomości", description = "Tylko administrator.")
  public EmailQueueService.EmailView get(@PathVariable Long id) {
    return emails.get(id);
  }
}
