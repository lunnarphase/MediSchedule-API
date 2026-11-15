import { stampLabel } from "../../api";
import { Stars } from "../../components";

export function ReviewList({ reviews }) {
  return (
    <>
      <div className="reviews-list">
        {reviews.map((review) => (
          <article className="review-card" key={review.id}>
            <div className="review-heading">
              <span className="avatar">{review.patientName?.[0] || "P"}</span>
              <div>
                <strong>{review.patientName || "Pacjent"}</strong>
                <time>{stampLabel(review.createdAt)}</time>
              </div>
              <Stars value={review.rating} />
            </div>
            {review.doctorName && (
              <p className="small-copy muted">Lekarz: {review.doctorName}</p>
            )}
            <p className="review-comment">
              {review.comment || "Opinia bez komentarza."}
            </p>
          </article>
        ))}
      </div>
    </>
  );
}
