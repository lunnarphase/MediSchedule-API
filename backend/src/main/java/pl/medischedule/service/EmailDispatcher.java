package pl.medischedule.service;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true", matchIfMissing = true)
public class EmailDispatcher {
  private final EmailQueueService queue;
  private final JavaMailSender sender;
  private final String from;
  private final boolean dispatcherEnabled;
  private final AtomicBoolean running = new AtomicBoolean();

  public EmailDispatcher(
      EmailQueueService queue,
      JavaMailSender sender,
      @Value("${app.mail.from}") String from,
      @Value("${app.mail.dispatcher-enabled:true}") boolean dispatcherEnabled) {
    this.queue = queue;
    this.sender = sender;
    this.from = from;
    this.dispatcherEnabled = dispatcherEnabled;
  }

  @Async("mailExecutor")
  @TransactionalEventListener
  public void onQueued(EmailQueueService.Queued event) {
    if (dispatcherEnabled) deliver();
  }

  @Async("mailExecutor")
  public void dispatch() {
    if (dispatcherEnabled) deliver();
  }

  public void deliver() {
    if (!running.compareAndSet(false, true)) return;
    try {
      for (EmailQueueService.Delivery job : queue.claim()) {
        try {
          SimpleMailMessage message = new SimpleMailMessage();
          message.setFrom(from);
          message.setTo(job.recipient());
          message.setSubject(job.subject());
          message.setText(job.body());
          sender.send(message);
          queue.sent(job);
        } catch (MailException error) {
          queue.failed(
              job, "Serwer poczty nie przyjął wiadomości. " + error.getClass().getSimpleName());
        }
      }
    } finally {
      running.set(false);
    }
  }
}
