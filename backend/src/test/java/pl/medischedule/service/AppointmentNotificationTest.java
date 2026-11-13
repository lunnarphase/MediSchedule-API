package pl.medischedule.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.Views;

class AppointmentNotificationTest {
  private final AppointmentRepository appointments = mock(AppointmentRepository.class);
  private final CurrentUser current = mock(CurrentUser.class);
  private final NotificationService notifications = mock(NotificationService.class);
  private final EmailOutbox outbox = mock(EmailOutbox.class);
  private final AppointmentService service =
      new AppointmentService(
          appointments,
          mock(DoctorRepository.class),
          mock(PatientRepository.class),
          mock(AvailabilityRepository.class),
          current,
          mock(Views.class),
          notifications,
          outbox);
  private Appointment appointment;

  @BeforeEach
  void setup() {
    User doctorUser = new User();
    doctorUser.id = 1L;
    doctorUser.name = "Jan Kowalski";
    Doctor doctor = new Doctor();
    doctor.id = 2L;
    doctor.user = doctorUser;
    User patientUser = new User();
    patientUser.id = 3L;
    patientUser.name = "Anna Nowak";
    Patient patient = new Patient();
    patient.user = patientUser;
    appointment = new Appointment();
    appointment.id = 5L;
    appointment.doctor = doctor;
    appointment.patient = patient;
    appointment.status = "confirmed";
    appointment.date = LocalDate.of(2026, 11, 8);
    appointment.time = LocalTime.of(9, 5);
    when(appointments.lockById(5L)).thenReturn(Optional.of(appointment));
  }

  @ParameterizedTest
  @CsvSource({"completed,zakończona", "cancelled,anulowana", "noshow,pacjent nieobecny"})
  void changedStatusUsesPolishTextAndIdentifiesAppointment(String status, String label) {
    when(current.require("doctor")).thenReturn(appointment.doctor.user);
    service.status(5L, status, null);
    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    verify(notifications)
        .add(eq(appointment.patient.user), message.capture(), eq("appointment"), eq(5L));
    assertEquals(
        "Wizyta: lekarz Jan Kowalski, 08.11.2026, godz. 09:05. Status: " + label + ".",
        message.getValue());
    assertFalse(message.getValue().contains(status));
  }

  @Test
  void reviewRequestIdentifiesTheCompletedAppointment() {
    when(current.require("doctor")).thenReturn(appointment.doctor.user);
    service.status(5L, "completed", "Zalecenia");
    verify(notifications)
        .add(
            appointment.patient.user,
            "Wizyta zakończona: lekarz Jan Kowalski, 08.11.2026, godz. 09:05. Możesz wystawić"
                + " opinię.",
            "review_request",
            5L);
  }

  @Test
  void cancellationIdentifiesPatientAndAppointmentTimeForDoctor() {
    when(current.require("patient")).thenReturn(appointment.patient.user);
    service.cancel(5L, "Zmiana planów");
    verify(notifications)
        .add(
            appointment.doctor.user,
            "Pacjent Anna Nowak anulował wizytę: 08.11.2026, godz. 09:05.",
            "appointment",
            5L);
  }

  @Test
  void doctorCancellationDoesNotReuseAnEarlierPatientCancellationReason() {
    appointment.cancelReason = "Stary powód pacjenta";
    appointment.cancelComment = "Stary komentarz";
    when(current.require("doctor")).thenReturn(appointment.doctor.user);

    service.status(5L, "cancelled", null);

    assertEquals("cancelled", appointment.status);
    assertNull(appointment.cancelReason);
    assertNull(appointment.cancelComment);
  }
}
