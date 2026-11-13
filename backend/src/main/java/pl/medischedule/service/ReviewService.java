package pl.medischedule.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.medischedule.domain.*;
import pl.medischedule.repository.*;
import pl.medischedule.security.CurrentUser;
import pl.medischedule.web.*;

@Service
@Transactional
public class ReviewService {
  private final ReviewRepository reviews;
  private final ReviewReportRepository reports;
  private final AppointmentRepository appointments;
  private final DoctorService doctors;
  private final CurrentUser current;
  private final NotificationService notifications;
  private final Views views;
  @PersistenceContext private EntityManager entityManager;

  public ReviewService(
      ReviewRepository reviews,
      ReviewReportRepository reports,
      AppointmentRepository appointments,
      DoctorService doctors,
      CurrentUser current,
      NotificationService notifications,
      Views views) {
    this.reviews = reviews;
    this.reports = reports;
    this.appointments = appointments;
    this.doctors = doctors;
    this.current = current;
    this.notifications = notifications;
    this.views = views;
  }

  public List<?> publicReviews(Long doctorId) {
    doctors.get(doctorId);
    return reviews.findByDoctorIdAndDeletedFalseOrderByCreatedAtDesc(doctorId).stream()
        .map(r -> views.review(r, false))
        .toList();
  }

  public List<?> mine() {
    Long id = doctors.mine().id;
    return reviews.findByDoctorIdAndDeletedFalseOrderByCreatedAtDesc(id).stream()
        .map(r -> views.review(r, true))
        .toList();
  }

  public Object submit(Long appointmentId, int rating, String comment) {
    var u = current.require("patient");
    Appointment a =
        appointments
            .lockById(appointmentId)
            .orElseThrow(() -> new ApiException(404, "Brak wizyty."));
    if (!a.patient.user.id.equals(u.id))
      throw new ApiException(403, "Możesz ocenić wyłącznie własną wizytę.");
    if (!a.status.equals("completed"))
      throw new ApiException(409, "Opinię można dodać po zakończeniu wizyty.");
    if (a.reviewSubmitted || reviews.existsByAppointmentId(a.id))
      throw new ApiException(409, "Ta wizyta została już oceniona.");
    if (rating < 1 || rating > 5 || comment != null && comment.length() > 3000)
      throw new ApiException(400, "Ocena musi wynosić 1–5, a komentarz maksymalnie 3000 znaków.");
    Review r = new Review();
    r.appointment = a;
    r.doctor = a.doctor;
    r.patient = a.patient;
    r.rating = rating;
    r.comment = comment == null ? "" : comment.trim();
    a.reviewSubmitted = true;
    reviews.saveAndFlush(r);
    notifications.add(a.doctor.user, "Otrzymano nową opinię po wizycie.", "new_review", r.id);
    return views.review(r, true);
  }

  public void report(Long id, String category, String reason) {
    var u = current.user();
    Review r =
        reviews
            .lockById(id)
            .filter(x -> !x.deleted)
            .orElseThrow(() -> new ApiException(404, "Brak opinii."));
    if (category == null
        || category.isBlank()
        || category.length() > 100
        || reason == null
        || reason.isBlank()
        || reason.length() > 2000)
      throw new ApiException(400, "Podaj kategorię i powód zgłoszenia.");
    if (reports.existsByReviewIdAndReporterIdAndStatus(id, u.id, "pending"))
      throw new ApiException(409, "Masz już otwarte zgłoszenie tej opinii.");
    ReviewReport report = new ReviewReport();
    report.review = r;
    report.reporter = u;
    report.category = category.trim();
    report.reason = reason.trim();
    reports.save(report);
    notifications.notifyAdmins("Nowe zgłoszenie opinii do moderacji.", "report");
  }

  public List<?> all() {
    current.require("admin");
    return reviews.findByDeletedFalseOrderByCreatedAtDesc().stream()
        .map(r -> views.review(r, true))
        .toList();
  }

  public List<?> reports() {
    current.require("admin");
    return reports.findByOrderByCreatedAtDesc().stream().map(views::report).toList();
  }

  public void delete(Long id) {
    current.require("admin");
    Review r = reviews.lockById(id).orElseThrow(() -> new ApiException(404, "Brak opinii."));
    remove(r, "Opinia została usunięta przez moderatora.");
  }

  private void remove(Review r, String response) {
    r.deleted = true;
    for (ReviewReport other : reports.findByReviewId(r.id)) {
      if (other.status.equals("pending")) {
        other.status = "resolved";
        other.adminResponse = response;
        notifications.add(
            other.reporter,
            moderationMessage(other, "Zgłoszona opinia o lekarzu ", " została usunięta."),
            "report_resolved",
            other.id);
      }
    }
  }

  public Object moderate(Long id, String action, String response) {
    current.require("admin");
    ReviewReport r =
        reports.findById(id).orElseThrow(() -> new ApiException(404, "Brak zgłoszenia."));
    reviews.lockById(r.review.id).orElseThrow();
    entityManager.refresh(r);
    if (!r.status.equals("pending"))
      throw new ApiException(409, "Zgłoszenie jest już rozpatrzone.");
    if (response != null && response.length() > 2000)
      throw new ApiException(400, "Odpowiedź jest za długa.");
    if ("resolve".equals(action)) {
      remove(r.review, response);
    } else if ("dismiss".equals(action)) {
      r.status = "dismissed";
      r.adminResponse = response;
      notifications.add(
          r.reporter,
          moderationMessage(r, "Zgłoszenie opinii o lekarzu ", " zostało odrzucone."),
          "report_dismissed",
          r.id);
    } else throw new ApiException(400, "Nieprawidłowa decyzja.");
    return views.report(r);
  }

  private String moderationMessage(ReviewReport report, String prefix, String outcome) {
    String message = prefix + report.review.doctor.user.name + outcome;
    if (report.adminResponse != null && !report.adminResponse.isBlank()) {
      message += " Odpowiedź administratora: " + report.adminResponse.trim();
    }
    return message;
  }
}
