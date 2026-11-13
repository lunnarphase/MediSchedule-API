package pl.medischedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentIntegrationTest extends ApiIntegrationTestSupport {
  @Test
  void reservationUsesFullDurationAndOnlyOwnerCanCancelIt() throws Exception {
    long id = book(patient, "09:00");
    requestBooking(otherPatient, doctorId, "09:30").andExpect(status().isConflict());
    mvc.perform(
            patch("/api/appointments/{id}/cancel", id)
                .session(otherPatient)
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Cudza wizyta\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/appointments").session(otherPatient))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mvc.perform(
            patch("/api/appointments/{id}/cancel", id)
                .session(patient)
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Zmiana planów\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"));
    book(otherPatient, "09:00");
  }

  @Test
  void simultaneousReservationsForOneDoctorCannotBothSucceed() throws Exception {
    assertThat(
            race(
                () ->
                    requestBooking(patient, doctorId, "09:00")
                        .andReturn()
                        .getResponse()
                        .getStatus(),
                () ->
                    requestBooking(otherPatient, doctorId, "09:00")
                        .andReturn()
                        .getResponse()
                        .getStatus()))
        .containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE id_doctor=?", Long.class, doctorId))
        .isEqualTo(1L);
  }

  @Test
  void simultaneousReservationsWithDifferentDoctorsCannotDoubleBookPatient() throws Exception {
    var second = createDoctor(UUID.randomUUID() + "@example.test");
    long secondId =
        body(mvc.perform(get("/api/doctor/profile").session(second)).andReturn())
            .get("id")
            .asLong();
    mvc.perform(
            put("/api/doctor/availability")
                .session(second)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "from",
                            day.toString(),
                            "to",
                            day.toString(),
                            "ranges",
                            List.of(
                                Map.of(
                                    "date",
                                    day.toString(),
                                    "startTime",
                                    "09:00",
                                    "endTime",
                                    "12:00"))))))
        .andExpect(status().isOk());
    assertThat(
            race(
                () ->
                    requestBooking(patient, doctorId, "09:00")
                        .andReturn()
                        .getResponse()
                        .getStatus(),
                () ->
                    requestBooking(patient, secondId, "09:00")
                        .andReturn()
                        .getResponse()
                        .getStatus()))
        .containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE id_patient=?", Long.class, patientId))
        .isEqualTo(1L);
  }

  @Test
  void bookedDurationIsStableAndEmailIsQueuedWithoutSmtp() throws Exception {
    book(patient, "09:00");
    mvc.perform(
            put("/api/doctor/profile")
                .session(doctor)
                .with(csrf())
                .contentType("application/json")
                .content("{\"bio\":\"Nowy czas\",\"visitPrice\":200,\"visitDuration\":30}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/appointments").session(patient))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].endTime").value("09:45"));
    String email =
        body(mvc.perform(get("/api/profile").session(patient)).andReturn()).get("email").asText();
    assertThat(
            jdbc.queryForObject(
                "SELECT body FROM email_jobs WHERE recipient=?", String.class, email))
        .contains("09:00–09:45");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM email_jobs WHERE recipient=?", String.class, email))
        .isEqualTo("pending");
  }

  @Test
  void onlyAssignedDoctorChangesStatusAndTerminalStatusCannotBeReopened() throws Exception {
    long id = book(patient, "09:00");
    var second = createDoctor(UUID.randomUUID() + "@example.test");
    mvc.perform(
            patch("/api/appointments/{id}/status", id)
                .session(second)
                .with(csrf())
                .contentType("application/json")
                .content("{\"status\":\"completed\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/appointments/{id}/status", id)
                .session(patient)
                .with(csrf())
                .contentType("application/json")
                .content("{\"status\":\"completed\"}"))
        .andExpect(status().isForbidden());
    complete(id);
    mvc.perform(
            patch("/api/appointments/{id}/status", id)
                .session(doctor)
                .with(csrf())
                .contentType("application/json")
                .content("{\"status\":\"confirmed\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            patch("/api/appointments/{id}/cancel", id)
                .session(patient)
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/appointments").session(patient))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("completed"))
        .andExpect(jsonPath("$[0].recommendations").value("Kontrola za miesiąc."));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE related_id=? AND type='review_request'",
                Long.class,
                id))
        .isEqualTo(1L);
  }

  @Test
  void bookingRejectsPastUnalignedAndOutsideWorkingHours() throws Exception {
    for (String time : List.of("08:00", "11:30", "09:00:15")) {
      requestBooking(patient, doctorId, time).andExpect(status().isBadRequest());
    }
    mvc.perform(
            post("/api/appointments")
                .session(patient)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "doctorId",
                            doctorId,
                            "date",
                            LocalDate.now(ZoneId.of("Europe/Warsaw")).minusDays(1).toString(),
                            "time",
                            "09:00"))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/appointments")
                .session(patient)
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    book(patient, "09:00");
  }

  @Test
  void administratorReschedulesUsingStoredDuration() throws Exception {
    long id = book(patient, "09:00");
    mvc.perform(
            put("/api/doctor/profile")
                .session(doctor)
                .with(csrf())
                .contentType("application/json")
                .content("{\"bio\":\"Krócej\",\"visitPrice\":200,\"visitDuration\":30}"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/admin/appointments/{id}", id)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "doctorId",
                            doctorId,
                            "date",
                            day.toString(),
                            "time",
                            "11:30",
                            "status",
                            "confirmed"))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/doctor/profile")
                .session(doctor)
                .with(csrf())
                .contentType("application/json")
                .content("{\"bio\":\"Dłużej\",\"visitPrice\":200,\"visitDuration\":60}"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/admin/appointments/{id}", id)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "doctorId",
                            doctorId,
                            "date",
                            day.toString(),
                            "time",
                            "11:15",
                            "status",
                            "confirmed"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.endTime").value("12:00"));
    mvc.perform(get("/api/admin/appointments").session(patient)).andExpect(status().isForbidden());
  }

  @Test
  void anotherPatientCannotDeleteSomeoneElsesNotification() throws Exception {
    book(patient, "09:00");
    var notices =
        body(
            mvc.perform(get("/api/notifications").session(patient))
                .andExpect(status().isOk())
                .andReturn());
    long id = notices.get(0).get("id").asLong();
    mvc.perform(delete("/api/notifications/{id}", id).session(otherPatient).with(csrf()))
        .andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE id=?", Long.class, id))
        .isEqualTo(1L);
    mvc.perform(delete("/api/notifications/{id}", id).session(patient).with(csrf()))
        .andExpect(status().isNoContent());
  }
}
