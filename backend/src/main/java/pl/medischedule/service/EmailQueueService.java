package pl.medischedule.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pl.medischedule.domain.*;
import pl.medischedule.repository.EmailJobRepository;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.*;

@Service
@Transactional
public class EmailQueueService {
  private final EmailJobRepository jobs;
  private final CurrentUser current;
  private final ApplicationEventPublisher events;
  private final boolean enabled;
  private final int maxAttempts;
  private final int retryDelay;

  public EmailQueueService(
      EmailJobRepository jobs,
      CurrentUser current,
      ApplicationEventPublisher events,
      @Value("${app.mail.enabled:true}") boolean enabled,
      @Value("${app.mail.max-attempts:5}") int maxAttempts,
      @Value("${app.mail.retry-delay-seconds:15}") int retryDelay) {
    this.jobs = jobs;
    this.current = current;
    this.events = events;
    this.enabled = enabled;
    this.maxAttempts = maxAttempts;
    this.retryDelay = retryDelay;
  }

  public record Queued() {}

  public record Delivery(Long id, int attempt, String recipient, String subject, String body) {}

  public record EmailView(
      Long id,
      Long appointmentId,
      String recipient,
      String subject,
      String body,
      String status,
      int attempts,
      Instant createdAt,
      Instant nextAttemptAt,
      Instant sentAt,
      String lastError) {}

  public void confirmation(Appointment appointment) {
    if (!enabled) return;
    String when =
        appointment.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            + ", godz. "
            + appointment.time.format(DateTimeFormatter.ofPattern("HH:mm"));
    String details =
        "Lekarz: "
            + appointment.doctor.user.name
            + "\nPacjent: "
            + appointment.patient.user.name
            + "\nTermin: "
            + when
            + "\nAktualny status wizyty znajdziesz w panelu MediSchedule.";
    enqueue(appointment.patient.user, appointment, "Potwierdzenie rezerwacji wizyty", details);
    enqueue(appointment.doctor.user, appointment, "Nowa rezerwacja wizyty", details);
    events.publishEvent(new Queued());
  }

  private void enqueue(User user, Appointment appointment, String subject, String body) {
    EmailJob job = new EmailJob();
    job.user = user;
    job.appointment = appointment;
    job.recipient = user.email;
    job.subject = subject;
    job.body = body;
    jobs.save(job);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Delivery> claim() {
    Instant now = Instant.now();
    List<Delivery> claimed = new ArrayList<>();
    for (EmailJob job : jobs.lockDue(now, 10)) {
      if (job.attempts >= maxAttempts) {
        job.status = "failed";
        job.leaseUntil = null;
        job.lastError = "Przekroczono limit prób wysyłki.";
        continue;
      }
      job.status = "processing";
      job.attempts++;
      job.leaseUntil = now.plusSeconds(180);
      claimed.add(new Delivery(job.id, job.attempts, job.recipient, job.subject, job.body));
    }
    return claimed;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void sent(Delivery delivery) {
    jobs.markSent(delivery.id, delivery.attempt, Instant.now());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(Delivery delivery, String error) {
    long delay = Math.min(900, retryDelay * (1L << Math.min(10, delivery.attempt - 1)));
    jobs.markFailed(
        delivery.id,
        delivery.attempt,
        delivery.attempt >= maxAttempts ? "failed" : "pending",
        Instant.now().plusSeconds(delay),
        error.substring(0, Math.min(error.length(), 1000)));
  }

  @Transactional(readOnly = true)
  public PageResult<EmailView> list(int page, int size, String status, String query) {
    current.require("admin");
    if (page < 0 || page > 1_000_000 || size < 1 || size > 100)
      throw new ApiException(400, "Nieprawidłowy numer lub rozmiar strony.");
    if (!status.isEmpty() && !Set.of("pending", "processing", "sent", "failed").contains(status))
      throw new ApiException(400, "Nieprawidłowy status wiadomości.");
    if (query.length() > 255) throw new ApiException(400, "Wyszukiwany tekst jest za długi.");
    Page<EmailView> result =
        jobs.search(
                status,
                query.toLowerCase(Locale.ROOT).trim(),
                PageRequest.of(
                    page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
            .map(EmailQueueService::view);
    return PageResult.from(result);
  }

  public void retry(Long id) {
    current.require("admin");
    if (!enabled) throw new ApiException(409, "Wysyłka e-mail jest wyłączona.");
    EmailJob job = jobs.lockById(id).orElseThrow(() -> new ApiException(404, "Brak wiadomości."));
    if (!job.status.equals("failed"))
      throw new ApiException(409, "Można ponowić tylko nieudaną wysyłkę.");
    job.status = "pending";
    job.attempts = 0;
    job.nextAttemptAt = Instant.now();
    job.lastError = null;
    job.leaseUntil = null;
    events.publishEvent(new Queued());
  }

  @Transactional(readOnly = true)
  public EmailView get(Long id) {
    current.require("admin");
    return view(jobs.findById(id).orElseThrow(() -> new ApiException(404, "Brak wiadomości.")));
  }

  private static EmailView view(EmailJob job) {
    return new EmailView(
        job.id,
        job.appointment == null ? null : job.appointment.id,
        job.recipient,
        job.subject,
        job.body,
        job.status,
        job.attempts,
        job.createdAt,
        job.nextAttemptAt,
        job.sentAt,
        job.lastError);
  }
}
