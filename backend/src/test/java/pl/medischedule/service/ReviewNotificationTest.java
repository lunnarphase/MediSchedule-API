package pl.medischedule.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.Views;

class ReviewNotificationTest {
  private final ReviewRepository reviews = mock(ReviewRepository.class);
  private final ReviewReportRepository reports = mock(ReviewReportRepository.class);
  private final NotificationService notifications = mock(NotificationService.class);
  private final ReviewService service =
      new ReviewService(
          reviews,
          reports,
          mock(AppointmentRepository.class),
          mock(DoctorService.class),
          mock(CurrentUser.class),
          notifications,
          mock(Views.class));
  private ReviewReport report;

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
    User doctorUser = new User();
    doctorUser.name = "Jan Kowalski";
    Doctor doctor = new Doctor();
    doctor.user = doctorUser;
    Review review = new Review();
    review.id = 5L;
    review.doctor = doctor;
    report = new ReviewReport();
    report.id = 8L;
    report.review = review;
    report.reporter = new User();
    report.status = "pending";
    when(reports.findById(8L)).thenReturn(Optional.of(report));
    when(reviews.lockById(5L)).thenReturn(Optional.of(review));
    when(reports.findByReviewId(5L)).thenReturn(List.of(report));
  }

  @ParameterizedTest
  @ValueSource(strings = {"resolve", "dismiss"})
  void moderationNotificationIdentifiesDoctorAndIncludesAdministratorResponse(String action) {
    service.moderate(8L, action, "  Wyjaśnienie decyzji moderatora.  ");

    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    verify(notifications).add(eq(report.reporter), message.capture(), anyString(), eq(8L));
    assertTrue(message.getValue().contains("Jan Kowalski"));
    assertTrue(
        message.getValue().contains("Odpowiedź administratora: Wyjaśnienie decyzji moderatora."));
    assertFalse(message.getValue().contains("  "));
    assertTrue(message.getValue().contains(action.equals("resolve") ? "usunięta" : "odrzucone"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"resolve", "dismiss"})
  void moderationWithoutResponseDoesNotExposeNullOrEmptyResponseLabel(String action) {
    service.moderate(8L, action, null);

    ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
    verify(notifications).add(eq(report.reporter), message.capture(), anyString(), eq(8L));
    assertTrue(message.getValue().contains("Jan Kowalski"));
    assertFalse(message.getValue().contains("null"));
    assertFalse(message.getValue().contains("Odpowiedź administratora:"));
  }
}
