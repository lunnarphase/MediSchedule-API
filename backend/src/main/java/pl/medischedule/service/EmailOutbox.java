package pl.medischedule.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.medischedule.domain.*;
import pl.medischedule.repository.EmailJobRepository;

@Service
public class EmailOutbox {
  private final EmailJobRepository jobs;

  public EmailOutbox(EmailJobRepository jobs) {
    this.jobs = jobs;
  }

  @Transactional
  public void appointment(Appointment a, String event) {
    EmailJob job = new EmailJob();
    job.recipient = a.patient.user.email;
    job.subject = "MediSchedule — " + event;
    job.body =
        "Dzień dobry, "
            + a.patient.user.name
            + ".\n\n"
            + event
            + "\nLekarz: "
            + a.doctor.user.name
            + "\nTermin: "
            + a.date
            + " "
            + a.time
            + "–"
            + a.endsAt.toLocalTime()
            + "\n\nSzczegóły znajdziesz w panelu Moje wizyty.\nMediSchedule";
    jobs.save(job);
  }
}
