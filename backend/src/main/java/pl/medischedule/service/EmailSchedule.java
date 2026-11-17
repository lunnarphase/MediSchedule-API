package pl.medischedule.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"app.mail.enabled", "app.mail.dispatcher-enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class EmailSchedule {
  private final EmailDispatcher dispatcher;

  public EmailSchedule(EmailDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Scheduled(fixedDelayString = "${app.mail.poll-interval-ms:5000}")
  public void checkQueue() {
    dispatcher.dispatch();
  }
}
