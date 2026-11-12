package pl.medischedule.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoData implements CommandLineRunner {
  private final UserRepository users;
  private final RoleRepository roles;
  private final PatientRepository patients;
  private final DoctorRepository doctors;
  private final AvailabilityRepository availability;
  private final SpecializationRepository specs;
  private final ScheduleTemplateRepository templates;
  private final AppointmentRepository appointments;
  private final ReviewRepository reviews;
  private final NotificationRepository notifications;
  private final PasswordEncoder passwords;

  public DemoData(
      UserRepository users,
      RoleRepository roles,
      PatientRepository patients,
      DoctorRepository doctors,
      AvailabilityRepository availability,
      SpecializationRepository specs,
      ScheduleTemplateRepository templates,
      AppointmentRepository appointments,
      ReviewRepository reviews,
      NotificationRepository notifications,
      PasswordEncoder passwords) {
    this.users = users;
    this.roles = roles;
    this.patients = patients;
    this.doctors = doctors;
    this.availability = availability;
    this.specs = specs;
    this.templates = templates;
    this.appointments = appointments;
    this.reviews = reviews;
    this.notifications = notifications;
    this.passwords = passwords;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (users.count() > 0) return;
    user("admin@medischedule.local", "Admin123!", "Administrator", "admin");
    Patient p = new Patient();
    p.user = user("patient@medischedule.local", "Patient123!", "Łukasz Pawlak", "patient");
    p.pesel = "90010112345";
    p.phone = "500 600 700";
    patients.save(p);
    Patient other = new Patient();
    other.user = user("patient2@medischedule.local", "Patient123!", "Anna Nowak", "patient");
    other.pesel = "92020223456";
    other.phone = "501 602 703";
    patients.save(other);
    String[] names = {"Jan Kowalski", "Anna Wiśniewska", "Piotr Zieliński", "Marek Wójcik"};
    String[] descriptions = {
      "Specjalista kardiologii. Diagnostyka i profilaktyka chorób serca.",
      "Opieka pediatryczna dla dzieci od pierwszych dni życia.",
      "Konsultacje neurologiczne i diagnostyka układu nerwowego.",
      "Kompleksowa opieka zdrowotna dla całej rodziny."
    };
    var specializations = specs.findAll();
    LocalDate today = LocalDate.now(DoctorService.ZONE);
    for (int i = 0; i < names.length; i++) {
      Doctor d = new Doctor();
      d.user =
          user(
              i == 0 ? "doctor@medischedule.local" : "doctor" + (i + 1) + "@medischedule.local",
              "Doctor123!",
              names[i],
              "doctor");
      d.bio = descriptions[i];
      d.visitPrice = new BigDecimal(170 + i * 25);
      d.visitDuration = i == 2 ? 45 : 30;
      d.specializations.add(specializations.get(i));
      doctors.save(d);
      for (int day = 0; day < 35; day++) {
        Availability a = new Availability();
        a.doctor = d;
        a.date = today.plusDays(day);
        a.startTime = LocalTime.of(9, 0);
        a.endTime = LocalTime.of(17, 0);
        availability.save(a);
      }
      ScheduleTemplate t = new ScheduleTemplate();
      t.doctor = d;
      t.name = "Dyżur poranny";
      t.startTime = LocalTime.of(9, 0);
      t.endTime = LocalTime.of(13, 0);
      templates.save(t);
      Appointment past = appointment(d, p, today.minusDays(i + 2), "completed");
      past.recommendations = "Kontrola zgodnie z ustaleniami. Dbaj o regularny odpoczynek.";
      appointments.save(past);
      Appointment rated = appointment(d, other, today.minusDays(i + 8), "completed");
      rated.reviewSubmitted = true;
      appointments.save(rated);
      Review r = new Review();
      r.appointment = rated;
      r.doctor = d;
      r.patient = other;
      r.rating = i % 2 == 0 ? 5 : 4;
      r.comment = "Bardzo dobry kontakt i jasne wyjaśnienie zaleceń.";
      reviews.save(r);
      if (i == 0) appointments.save(appointment(d, p, today.plusDays(2), "confirmed"));
    }
    Notification n = new Notification();
    n.user = p.user;
    n.message = "Witaj w MediSchedule. Znajdź specjalistę i zarezerwuj dogodny termin.";
    n.type = "general";
    notifications.save(n);
  }

  private User user(String email, String password, String name, String role) {
    User u = new User();
    u.email = email;
    u.password = passwords.encode(password);
    u.name = name;
    u.role = roles.findByName(role).orElseThrow();
    return users.save(u);
  }

  private Appointment appointment(Doctor d, Patient p, LocalDate date, String status) {
    Appointment a = new Appointment();
    a.doctor = d;
    a.patient = p;
    a.date = date;
    a.time = LocalTime.of(10, 0);
    a.startsAt = date.atTime(a.time);
    a.endsAt = a.startsAt.plusMinutes(d.visitDuration);
    a.status = status;
    return a;
  }
}
