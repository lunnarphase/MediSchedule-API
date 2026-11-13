package pl.medischedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScheduleIntegrationTest extends ApiIntegrationTestSupport {
  @Test
  void slotsFitTheWholeVisitInsideSeparateAvailabilityRanges() throws Exception {
    mvc.perform(
            put("/api/doctor/availability")
                .session(doctor)
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
                                    "10:30"),
                                Map.of(
                                    "date",
                                    day.toString(),
                                    "startTime",
                                    "11:00",
                                    "endTime",
                                    "12:00"))))))
        .andExpect(status().isOk());
    var slots =
        body(
            mvc.perform(
                    get("/api/doctors/{id}/slots", doctorId)
                        .session(patient)
                        .param("date", day.toString()))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(slots).hasSize(3);
    assertThat(slots.get(0).asText()).isEqualTo("09:00");
    assertThat(slots.get(1).asText()).isEqualTo("09:45");
    assertThat(slots.get(2).asText()).isEqualTo("11:00");
    mvc.perform(
            get("/api/doctors/{id}/available-dates", doctorId)
                .session(patient)
                .param("from", day.minusDays(1).toString())
                .param("to", day.plusDays(1).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value(day.toString()))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "[null]",
        "[{\"date\":\"$DATE\",\"startTime\":null,\"endTime\":\"12:00\"}]",
        "[{\"date\":\"$DATE\",\"startTime\":\"10:00\",\"endTime\":\"09:00\"}]",
        "[{\"date\":\"$DATE\",\"startTime\":\"09:00:30\",\"endTime\":\"12:00\"}]"
      })
  void invalidRangesDoNotEraseSavedSchedule(String ranges) throws Exception {
    String request =
        String.format(
            "{\"from\":\"%s\",\"to\":\"%s\",\"ranges\":%s}",
            day, day, ranges.replace("$DATE", day.toString()));
    mvc.perform(
            put("/api/doctor/availability")
                .session(doctor)
                .with(csrf())
                .contentType("application/json")
                .content(request))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/doctor/availability")
                .session(doctor)
                .param("from", day.toString())
                .param("to", day.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].startTime").value("09:00"));
  }

  @Test
  void tooLongOrReversedDateRangeIsRejected() throws Exception {
    mvc.perform(
            get("/api/doctors/{id}/available-dates", doctorId)
                .session(patient)
                .param("from", day.toString())
                .param("to", day.plusDays(94).toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/doctor/availability")
                .session(doctor)
                .param("from", day.toString())
                .param("to", day.minusDays(1).toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void scheduleCannotExcludeAnExistingReservation() throws Exception {
    jdbc.update(
        "INSERT INTO"
            + " appointments(id_doctor,id_patient,appointment_date,appointment_time,starts_at,ends_at)"
            + " VALUES (?,?,?,?,?,?)",
        doctorId,
        patientId,
        day,
        LocalTime.of(9, 0),
        day.atTime(9, 0),
        day.atTime(9, 45));
    replaceAvailability("10:00", "12:00").andExpect(status().isConflict());
    mvc.perform(
            get("/api/doctor/availability")
                .session(doctor)
                .param("from", day.toString())
                .param("to", day.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].startTime").value("09:00"));
  }

  @Test
  void onlyTemplateOwnerCanDeleteItAndInvalidHoursAreRejected() throws Exception {
    long id =
        body(mvc.perform(
                    post("/api/doctor/templates")
                        .session(doctor)
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"Poranek\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}"))
                .andExpect(status().isCreated())
                .andReturn())
            .get("id")
            .asLong();
    var otherDoctor = createDoctor(UUID.randomUUID() + "@example.test");
    mvc.perform(delete("/api/doctor/templates/{id}", id).session(otherDoctor).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/doctor/templates")
                .session(doctor)
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Błędny\",\"startTime\":\"12:00\",\"endTime\":\"09:00\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/doctor/templates/{id}", id).session(doctor).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/doctor/templates").session(doctor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void scheduleEditingRequiresDoctorRoleAndCsrf() throws Exception {
    mvc.perform(
            get("/api/doctor/availability")
                .param("from", day.toString())
                .param("to", day.toString()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/doctor/templates").session(patient)).andExpect(status().isForbidden());
    mvc.perform(
            put("/api/doctor/availability")
                .session(patient)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("from", day.toString(), "to", day.toString(), "ranges", List.of()))))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/doctor/availability")
                .session(doctor)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
  }
}
