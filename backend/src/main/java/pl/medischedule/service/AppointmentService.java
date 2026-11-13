package pl.medischedule.service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.*;

@Service
@Transactional
public class AppointmentService {
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
  private final AppointmentRepository appointments;
  private final DoctorRepository doctors;
  private final PatientRepository patients;
  private final AvailabilityRepository availability;
  private final CurrentUser current;
  private final Views views;
  private final NotificationService notifications;
  private final EmailOutbox outbox;

  public AppointmentService(
      AppointmentRepository appointments,
      DoctorRepository doctors,
      PatientRepository patients,
      AvailabilityRepository availability,
      CurrentUser current,
      Views views,
      NotificationService notifications,
      EmailOutbox outbox) {
    this.appointments = appointments;
    this.doctors = doctors;
    this.patients = patients;
    this.availability = availability;
    this.current = current;
    this.views = views;
    this.notifications = notifications;
    this.outbox = outbox;
  }

  public List<Map<String, Object>> list() {
    var u = current.user();
    List<Appointment> result;
    switch (u.role.name) {
      case "admin" -> result = appointments.findAll();
      case "doctor" ->
          result =
              appointments.findByDoctorIdOrderByDateDescTimeDesc(
                  doctors.findByUserId(u.id).orElseThrow().id);
      default -> result = appointments.findByPatientUserIdOrderByDateDescTimeDesc(u.id);
    }
    return result.stream()
        .sorted(Comparator.comparing((Appointment a) -> a.startsAt).reversed())
        .map(views::appointment)
        .toList();
  }

  public Map<String, Object> book(Long doctorId, LocalDate date, LocalTime time) {
    User u = current.require("patient");
    Doctor d = lockDoctor(doctorId);
    Patient p =
        patients
            .findByUserId(u.id)
            .orElseThrow(() -> new ApiException(404, "Brak profilu pacjenta."));
    checkSlot(d, date, time, d.visitDuration, null, p);
    Appointment a = new Appointment();
    a.patient = p;
    a.doctor = d;
    setTime(a, date, time, d.visitDuration);
    appointments.saveAndFlush(a);
    notifications.add(
        p.user, "Zarezerwowano wizytę: " + visitDescription(a) + ".", "appointment", a.id);
    notifications.add(
        d.user,
        "Nowa rezerwacja: pacjent " + p.user.name + ", " + visitDateTime(a) + ".",
        "appointment",
        a.id);
    outbox.appointment(a, "Potwierdzenie rezerwacji wizyty");
    return views.appointment(a);
  }

  private Doctor lockDoctor(Long id) {
    if (id == null) throw new ApiException(400, "Wybierz lekarza.");
    return doctors.lockById(id).orElseThrow(() -> new ApiException(404, "Nie znaleziono lekarza."));
  }

  private Appointment lockAppointment(Long id) {
    return appointments
        .lockById(id)
        .orElseThrow(() -> new ApiException(404, "Nie znaleziono wizyty."));
  }

  private void setTime(Appointment a, LocalDate date, LocalTime time, int duration) {
    a.date = date;
    a.time = time;
    a.startsAt = date.atTime(time);
    a.endsAt = a.startsAt.plusMinutes(duration);
  }

  private String visitDateTime(Appointment appointment) {
    return appointment.date.format(DATE_FORMAT) + ", godz. " + appointment.time.format(TIME_FORMAT);
  }

  private String visitDescription(Appointment appointment) {
    return "lekarz " + appointment.doctor.user.name + ", " + visitDateTime(appointment);
  }

  private String statusLabel(String status) {
    return switch (status) {
      case "confirmed" -> "potwierdzona";
      case "completed" -> "zakończona";
      case "cancelled" -> "anulowana";
      case "noshow" -> "pacjent nieobecny";
      default -> "zaktualizowana";
    };
  }

  private void checkSlot(
      Doctor d, LocalDate date, LocalTime time, int duration, Long ignoreId, Patient patient) {
    if (date == null
        || time == null
        || (time.getSecond() != 0 || time.getNano() != 0)
        || !date.atTime(time).isAfter(LocalDateTime.now(DoctorService.ZONE)))
      throw new ApiException(400, "Wybierz przyszły termin wizyty.");
    if (d.user.blocked || !d.user.role.name.equals("doctor"))
      throw new ApiException(409, "Lekarz jest niedostępny.");
    LocalDateTime start = date.atTime(time), end = start.plusMinutes(duration);
    if (!end.toLocalDate().equals(date))
      throw new ApiException(400, "Wizyta musi kończyć się tego samego dnia.");
    if (appointments.overlaps(d.id, start, end).stream().anyMatch(a -> !a.id.equals(ignoreId)))
      throw new ApiException(409, "Wybrany termin koliduje z inną wizytą.");
    if (appointments.findByPatientUserIdOrderByDateDescTimeDesc(patient.user.id).stream()
        .anyMatch(
            a ->
                !a.id.equals(ignoreId)
                    && !a.status.equals("cancelled")
                    && start.isBefore(a.endsAt)
                    && end.isAfter(a.startsAt)))
      throw new ApiException(409, "Pacjent ma już wizytę w tym czasie.");
    boolean valid =
        availability
            .findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(d.id, date, date)
            .stream()
            .anyMatch(
                r ->
                    !time.isBefore(r.startTime)
                        && !end.toLocalTime().isAfter(r.endTime)
                        && Duration.between(r.startTime, time).toMinutes() % duration == 0);
    if (!valid) throw new ApiException(400, "Termin nie należy do dostępnych godzin lekarza.");
  }

  public Object cancel(Long id, String reason) {
    User u = current.require("patient");
    Appointment a = lockAppointment(id);
    if (!a.patient.user.id.equals(u.id)) throw new ApiException(403, "Brak dostępu do wizyty.");
    if (!a.status.equals("confirmed"))
      throw new ApiException(409, "Można anulować tylko potwierdzoną wizytę.");
    if (reason != null && reason.length() > 1000)
      throw new ApiException(400, "Powód jest za długi.");
    a.status = "cancelled";
    a.cancelReason = reason;
    notifications.add(
        a.doctor.user,
        "Pacjent " + a.patient.user.name + " anulował wizytę: " + visitDateTime(a) + ".",
        "appointment",
        a.id);
    outbox.appointment(a, "Anulowanie wizyty");
    return views.appointment(a);
  }

  public Object status(Long id, String status, String recommendations) {
    User u = current.require("doctor");
    Appointment a = lockAppointment(id);
    if (!a.doctor.user.id.equals(u.id)) throw new ApiException(403, "Brak dostępu do wizyty.");
    if (status == null
        || !Set.of("confirmed", "completed", "cancelled", "noshow").contains(status)
        || recommendations != null && recommendations.length() > 10000)
      throw new ApiException(400, "Nieprawidłowy status lub zalecenia.");
    if (!a.status.equals("confirmed") && !a.status.equals(status))
      throw new ApiException(409, "Zakończona lub anulowana wizyta nie może zmienić statusu.");
    boolean changed = !a.status.equals(status);
    a.status = status;
    a.recommendations = recommendations;
    if (changed || !status.equals("cancelled")) {
      a.cancelReason = null;
      a.cancelComment = null;
    }
    if (changed) {
      notifications.add(
          a.patient.user,
          "Wizyta: " + visitDescription(a) + ". Status: " + statusLabel(status) + ".",
          "appointment",
          a.id);
      if (status.equals("completed"))
        notifications.add(
            a.patient.user,
            "Wizyta zakończona: " + visitDescription(a) + ". Możesz wystawić opinię.",
            "review_request",
            a.id);
      if (status.equals("cancelled")) outbox.appointment(a, "Anulowanie wizyty przez lekarza");
    }
    return views.appointment(a);
  }

  public Object adminUpdate(
      Long id, Long doctorId, Long patientId, LocalDate date, LocalTime time, String status) {
    current.require("admin");
    Doctor d = lockDoctor(doctorId);
    Appointment a = lockAppointment(id);
    Patient p =
        patientId == null
            ? a.patient
            : patients
                .findById(patientId)
                .orElseThrow(() -> new ApiException(404, "Brak pacjenta."));
    if (status == null || !Set.of("confirmed", "completed", "cancelled", "noshow").contains(status))
      throw new ApiException(400, "Nieprawidłowy status.");
    if (a.reviewSubmitted && (!d.id.equals(a.doctor.id) || !p.id.equals(a.patient.id)))
      throw new ApiException(409, "Nie można zmieniać uczestników ocenionej wizyty.");
    if (date == null || time == null) throw new ApiException(400, "Brak terminu.");
    int duration = (int) Duration.between(a.startsAt, a.endsAt).toMinutes();
    if (!d.id.equals(a.doctor.id)) duration = d.visitDuration;
    if (!status.equals("cancelled")
        && (!date.equals(a.date)
            || !time.equals(a.time)
            || !d.id.equals(a.doctor.id)
            || !p.id.equals(a.patient.id)
            || a.status.equals("cancelled"))) checkSlot(d, date, time, duration, id, p);
    a.doctor = d;
    a.patient = p;
    a.status = status;
    if (!status.equals("cancelled")) {
      a.cancelReason = null;
      a.cancelComment = null;
    }
    setTime(a, date, time, duration);
    appointments.flush();
    notifications.add(
        p.user,
        "Administrator zmienił szczegóły wizyty: " + visitDescription(a) + ".",
        "appointment",
        a.id);
    return views.appointment(a);
  }

  public void adminDelete(Long id) {
    current.require("admin");
    appointments.delete(lockAppointment(id));
  }
}
