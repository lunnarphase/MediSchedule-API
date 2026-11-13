package pl.medischedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

class ReviewIntegrationTest extends ApiIntegrationTestSupport {
  @Test
  void onlyOwnerCanReviewCompletedVisitOnceEvenAfterModeration() throws Exception {
    long appointment = book(patient, "09:00");
    submit(patient, appointment, 5, "Konsultacja").andExpect(status().isConflict());
    complete(appointment);
    submit(otherPatient, appointment, 5, "Cudza wizyta").andExpect(status().isForbidden());
    submit(doctor, appointment, 5, "Lekarz").andExpect(status().isForbidden());
    long review =
        body(submit(patient, appointment, 5, "Rzetelna konsultacja")
                .andExpect(status().isCreated())
                .andReturn())
            .get("id")
            .asLong();
    submit(patient, appointment, 5, "Druga opinia").andExpect(status().isConflict());
    mvc.perform(
            put("/api/admin/appointments/{id}", appointment)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "doctorId",
                            doctorId,
                            "patientId",
                            otherPatientId,
                            "date",
                            day.toString(),
                            "time",
                            "09:00",
                            "status",
                            "completed"))))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/admin/reviews/{id}", review).session(admin).with(csrf()))
        .andExpect(status().isNoContent());
    submit(patient, appointment, 5, "Ponownie po usunięciu").andExpect(status().isConflict());
    mvc.perform(get("/api/doctors/{id}/reviews", doctorId).session(patient))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 6})
  void invalidRatingOrLongCommentDoesNotConsumeTheRightToReview(int rating) throws Exception {
    long appointment = book(patient, "09:00");
    complete(appointment);
    submit(patient, appointment, rating, "Błędna ocena").andExpect(status().isBadRequest());
    submit(patient, appointment, 5, "x".repeat(3001)).andExpect(status().isBadRequest());
    submit(patient, appointment, 1, "")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rating").value(1));
    mvc.perform(get("/api/doctors/{id}/reviews", doctorId).session(otherPatient))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].patientName").value("Pacjent T."));
    mvc.perform(get("/api/doctor/reviews").session(doctor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].patientName").value("Pacjent Testowy"));
  }

  @Test
  void simultaneousReviewsCreateOneReviewAndOneNotification() throws Exception {
    long appointment = book(patient, "09:00");
    complete(appointment);
    assertThat(
            race(
                () ->
                    submit(patient, appointment, 5, "Pierwsza")
                        .andReturn()
                        .getResponse()
                        .getStatus(),
                () ->
                    submit(patient, appointment, 4, "Druga").andReturn().getResponse().getStatus()))
        .containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM reviews WHERE id_appointment=?", Long.class, appointment))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notifications n JOIN reviews r ON n.related_id=r.id"
                    + " WHERE r.id_appointment=? AND n.type='new_review'",
                Long.class,
                appointment))
        .isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"resolve", "dismiss"})
  void reportsPreventDuplicatesAndPreserveReviewContextAfterDecision(String action)
      throws Exception {
    long appointment = book(patient, "09:00");
    complete(appointment);
    long review =
        body(submit(patient, appointment, 4, "Treść do sprawdzenia")
                .andExpect(status().isCreated())
                .andReturn())
            .get("id")
            .asLong();
    String reportBody = "{\"category\":\"Treść\",\"reason\":\"Proszę o sprawdzenie\"}";
    mvc.perform(
            post("/api/reviews/{id}/reports", review)
                .session(otherPatient)
                .with(csrf())
                .contentType("application/json")
                .content(reportBody))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/reviews/{id}/reports", review)
                .session(otherPatient)
                .with(csrf())
                .contentType("application/json")
                .content(reportBody))
        .andExpect(status().isConflict());
    JsonNode report = findReport(review);
    long reportId = report.get("id").asLong();
    String decision =
        json.writeValueAsString(Map.of("action", action, "response", "Zweryfikowano treść."));
    mvc.perform(get("/api/admin/reports").session(patient)).andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/admin/reports/{id}", reportId)
                .session(otherPatient)
                .with(csrf())
                .contentType("application/json")
                .content(decision))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/admin/reports/{id}", reportId)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content("{\"action\":\"invalid\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/admin/reports/{id}", reportId)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(decision))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(action.equals("resolve") ? "resolved" : "dismissed"))
        .andExpect(jsonPath("$.review.comment").value("Treść do sprawdzenia"))
        .andExpect(jsonPath("$.review.deleted").value(action.equals("resolve")));
    mvc.perform(
            patch("/api/admin/reports/{id}", reportId)
                .session(admin)
                .with(csrf())
                .contentType("application/json")
                .content(decision))
        .andExpect(status().isConflict());
    assertThat(findReport(review).get("review").get("patientName").asText())
        .isEqualTo("Pacjent Testowy");
    String type = action.equals("resolve") ? "report_resolved" : "report_dismissed";
    var notices = body(mvc.perform(get("/api/notifications").session(otherPatient)).andReturn());
    boolean notified = false;
    for (JsonNode notice : notices) {
      if (notice.get("type").asText().equals(type)
          && notice.get("relatedId").asLong() == reportId) {
        assertThat(notice.get("message").asText())
            .contains("Lekarz Testowy", "Zweryfikowano treść.");
        notified = true;
      }
    }
    assertThat(notified).isTrue();
    var publicReviews =
        body(mvc.perform(get("/api/doctors/{id}/reviews", doctorId).session(patient)).andReturn());
    assertThat(publicReviews).hasSize(action.equals("resolve") ? 0 : 1);
  }

  private ResultActions submit(
      MockHttpSession session, long appointment, int rating, String comment) throws Exception {
    return mvc.perform(
        post("/api/appointments/{id}/review", appointment)
            .session(session)
            .with(csrf())
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("rating", rating, "comment", comment))));
  }

  private JsonNode findReport(long review) throws Exception {
    var reports =
        body(
            mvc.perform(get("/api/admin/reports").session(admin))
                .andExpect(status().isOk())
                .andReturn());
    for (JsonNode report : reports) {
      if (report.get("reviewId").asLong() == review) return report;
    }
    throw new IllegalStateException("Nie znaleziono zgłoszenia testowego");
  }
}
