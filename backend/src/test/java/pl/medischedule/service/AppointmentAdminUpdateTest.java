package pl.medischedule.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.Views;

class AppointmentAdminUpdateTest {
  private final AppointmentRepository appointments = mock(AppointmentRepository.class);
  private final DoctorRepository doctors = mock(DoctorRepository.class);
  private final AvailabilityRepository availability = mock(AvailabilityRepository.class);
  private final CurrentUser current = mock(CurrentUser.class);
  private final AppointmentService service =
      new AppointmentService(
          appointments,
          doctors,
          mock(PatientRepository.class),
          availability,
          current,
          mock(Views.class),
          mock(NotificationService.class),
          mock(EmailOutbox.class));
  private Appointment appointment;
  private LocalDate date;

  @BeforeEach
  void setup() {
    date = LocalDate.now(DoctorService.ZONE).plusDays(2);
    Doctor doctor = new Doctor();
    doctor.id = 2L;
    doctor.user = new User();
    doctor.user.name = "Jan Kowalski";
    doctor.user.role = new Role();
    doctor.user.role.name = "doctor";
    Patient patient = new Patient();
    patient.id = 3L;
    patient.user = new User();
    patient.user.id = 4L;
    appointment = new Appointment();
    appointment.id = 5L;
    appointment.doctor = doctor;
    appointment.patient = patient;
    appointment.status = "cancelled";
    appointment.date = date;
    appointment.time = LocalTime.of(14, 0);
    appointment.startsAt = date.atTime(14, 0);
    appointment.endsAt = date.atTime(14, 45);
    appointment.cancelReason = "Zmiana planów pacjenta";
    appointment.cancelComment = "Poprzedni termin nie pasował";
    when(doctors.lockById(2L)).thenReturn(Optional.of(doctor));
    when(appointments.lockById(5L)).thenReturn(Optional.of(appointment));
  }

  @ParameterizedTest
  @ValueSource(strings = {"confirmed", "completed", "noshow"})
  void leavingCancelledStatusClearsObsoleteCancellationDetails(String status) {
    Availability range = new Availability();
    range.date = date;
    range.startTime = LocalTime.of(16, 0);
    range.endTime = LocalTime.of(18, 0);
    when(availability.findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(2L, date, date))
        .thenReturn(List.of(range));

    service.adminUpdate(5L, 2L, null, date, LocalTime.of(16, 0), status);

    verify(current).require("admin");
    assertEquals(status, appointment.status);
    assertNull(appointment.cancelReason);
    assertNull(appointment.cancelComment);
    assertEquals(date.atTime(16, 45), appointment.endsAt);
    verify(appointments).flush();
  }

  @Test
  void editingCancelledAppointmentPreservesItsCancellationDetails() {
    service.adminUpdate(5L, 2L, null, date, LocalTime.of(16, 0), "cancelled");

    assertEquals("Zmiana planów pacjenta", appointment.cancelReason);
    assertEquals("Poprzedni termin nie pasował", appointment.cancelComment);
    assertEquals("cancelled", appointment.status);
    verifyNoInteractions(availability);
  }
}
