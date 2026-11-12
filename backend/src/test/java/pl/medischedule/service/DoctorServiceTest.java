package pl.medischedule.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.*;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {
  @Mock DoctorRepository doctors;
  @Mock AvailabilityRepository availability;
  @Mock ScheduleTemplateRepository templates;
  @Mock AppointmentRepository appointments;
  @Mock SpecializationRepository specializations;
  @Mock CurrentUser current;
  @Mock Views views;
  DoctorService service;

  @BeforeEach
  void setup() {
    service =
        new DoctorService(
            doctors, availability, templates, appointments, specializations, current, views);
  }

  @Test
  void slotsRespectDurationAndPartialOverlap() {
    LocalDate date = LocalDate.now(DoctorService.ZONE).plusDays(7);
    Doctor doctor = doctor();
    doctor.visitDuration = 45;
    Availability range = new Availability();
    range.date = date;
    range.startTime = LocalTime.of(9, 0);
    range.endTime = LocalTime.NOON;
    Appointment taken = new Appointment();
    taken.startsAt = date.atTime(9, 30);
    taken.endsAt = date.atTime(10, 15);
    when(doctors.findById(1L)).thenReturn(Optional.of(doctor));
    when(availability.findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(1L, date, date))
        .thenReturn(List.of(range));
    when(appointments.overlaps(1L, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
        .thenReturn(List.of(taken));
    assertEquals(List.of("10:30", "11:15"), service.slots(1L, date));
  }

  @Test
  void cannotRemoveAvailabilityCoveringExistingReservation() {
    LocalDate date = LocalDate.now(DoctorService.ZONE).plusDays(7);
    Doctor doctor = doctor();
    Appointment appointment = new Appointment();
    appointment.date = date;
    appointment.time = LocalTime.of(9, 0);
    appointment.startsAt = date.atTime(9, 0);
    appointment.endsAt = date.atTime(9, 30);
    when(current.require("doctor")).thenReturn(doctor.user);
    when(doctors.findByUserId(doctor.user.id)).thenReturn(Optional.of(doctor));
    when(doctors.lockById(1L)).thenReturn(Optional.of(doctor));
    when(appointments.overlaps(1L, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
        .thenReturn(List.of(appointment));
    assertThrows(
        ApiException.class,
        () ->
            service.saveWeek(
                date,
                date,
                List.of(new DoctorService.Range(date, LocalTime.of(10, 0), LocalTime.NOON))));
    verify(availability, never()).deleteAll(anyIterable());
  }

  @Test
  void doctorCannotDeleteAnotherDoctorsTemplate() {
    Doctor doctor = doctor();
    Doctor other = doctor();
    other.id = 2L;
    ScheduleTemplate template = new ScheduleTemplate();
    template.id = 8L;
    template.doctor = other;
    when(templates.findById(8L)).thenReturn(Optional.of(template));
    when(current.require("doctor")).thenReturn(doctor.user);
    when(doctors.findByUserId(doctor.user.id)).thenReturn(Optional.of(doctor));
    assertThrows(ApiException.class, () -> service.deleteTemplate(8L));
    verify(templates, never()).delete(any());
  }

  private Doctor doctor() {
    Doctor doctor = new Doctor();
    doctor.id = 1L;
    doctor.user = new User();
    doctor.user.id = 10L;
    doctor.user.role = new Role();
    doctor.user.role.name = "doctor";
    return doctor;
  }
}
