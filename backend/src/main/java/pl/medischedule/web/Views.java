package pl.medischedule.web;

import java.util.*;
import org.springframework.stereotype.Component;
import pl.medischedule.domain.*;
import pl.medischedule.repository.ReviewRepository;

@Component
public class Views {
  private final ReviewRepository reviews;

  public Views(ReviewRepository reviews) {
    this.reviews = reviews;
  }

  public static Map<String, Object> data(Object... pairs) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], pairs[i + 1]);
    return out;
  }

  public Map<String, Object> doctor(Doctor d) {
    var rs = reviews.findByDoctorIdAndDeletedFalseOrderByCreatedAtDesc(d.id);
    return data(
        "id",
        d.id,
        "userId",
        d.user.id,
        "name",
        d.user.name,
        "bio",
        d.bio,
        "visitPrice",
        d.visitPrice,
        "visitDuration",
        d.visitDuration,
        "specializations",
        d.specializations.stream().map(s -> s.name).sorted().toList(),
        "averageRating",
        rs.stream().mapToInt(r -> r.rating).average().orElse(0),
        "reviewCount",
        rs.size());
  }

  public Map<String, Object> appointment(Appointment a) {
    return data(
        "id",
        a.id,
        "doctorId",
        a.doctor.id,
        "patientId",
        a.patient.id,
        "doctorName",
        a.doctor.user.name,
        "patientName",
        a.patient.user.name,
        "date",
        a.date,
        "time",
        a.time.toString(),
        "endTime",
        a.endsAt.toLocalTime().toString(),
        "status",
        a.status,
        "recommendations",
        a.recommendations,
        "cancelReason",
        a.cancelReason,
        "reviewSubmitted",
        a.reviewSubmitted);
  }

  public Map<String, Object> review(Review r, boolean fullName) {
    String name = r.patient.user.name;
    if (!fullName) {
      var words = name.trim().split("\\s+");
      name =
          words[0] + (words.length > 1 ? " " + words[words.length - 1].substring(0, 1) + "." : "");
    }
    return data(
        "id",
        r.id,
        "doctorId",
        r.doctor.id,
        "doctorName",
        r.doctor.user.name,
        "patientName",
        name,
        "rating",
        r.rating,
        "comment",
        r.comment,
        "createdAt",
        r.createdAt);
  }

  public Map<String, Object> report(ReviewReport r) {
    Map<String, Object> review = review(r.review, true);
    review.put("deleted", r.review.deleted);
    return data(
        "id",
        r.id,
        "reviewId",
        r.review.id,
        "review",
        review,
        "reporterName",
        r.reporter.name,
        "category",
        r.category,
        "reason",
        r.reason,
        "status",
        r.status,
        "adminResponse",
        r.adminResponse,
        "createdAt",
        r.createdAt);
  }

  public Map<String, Object> notification(Notification n) {
    return data(
        "id",
        n.id,
        "message",
        n.message,
        "type",
        n.type,
        "read",
        n.read,
        "createdAt",
        n.createdAt,
        "relatedId",
        n.relatedId);
  }
}
