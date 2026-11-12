package pl.medischedule.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.medischedule.domain.*;
import pl.medischedule.repository.ReviewRepository;

class ViewsReportTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reportRetainsReviewContextAfterSoftDeletion(boolean deleted) {
    User doctorUser = new User();
    doctorUser.name = "Jan Kowalski";
    Doctor doctor = new Doctor();
    doctor.id = 2L;
    doctor.user = doctorUser;
    User patientUser = new User();
    patientUser.name = "Anna Nowak";
    Patient patient = new Patient();
    patient.user = patientUser;
    Review review = new Review();
    review.id = 5L;
    review.doctor = doctor;
    review.patient = patient;
    review.rating = 4;
    review.comment = "Treść opinii zachowana do audytu";
    review.deleted = deleted;
    ReviewReport report = new ReviewReport();
    report.id = 8L;
    report.review = review;
    report.reporter = patientUser;
    report.status = deleted ? "resolved" : "pending";

    Map<String, Object> result = new Views(mock(ReviewRepository.class)).report(report);

    assertEquals(5L, result.get("reviewId"));
    Map<?, ?> nested = assertInstanceOf(Map.class, result.get("review"));
    assertEquals("Jan Kowalski", nested.get("doctorName"));
    assertEquals("Anna Nowak", nested.get("patientName"));
    assertEquals(4, nested.get("rating"));
    assertEquals(review.comment, nested.get("comment"));
    assertEquals(deleted, nested.get("deleted"));
  }
}
