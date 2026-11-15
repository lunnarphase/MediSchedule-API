import { useState } from "react";
import { api, dateLabel, stampLabel } from "../../api";
import {
  Button,
  ErrorBox,
  Icon,
  Modal,
  Select,
  Stars,
  Textarea,
  useApp,
  useMutation,
} from "../../components";

export function ReviewModal({ appointment, onClose }) {
  const { changed, notify } = useApp(),
    mutation = useMutation();
  const [rating, setRating] = useState(5);
  async function submit(event) {
    event.preventDefault();
    const body = {
      ...Object.fromEntries(new FormData(event.currentTarget)),
      rating,
    };
    await mutation.run(async () => {
      await api(`/appointments/${appointment.id}/review`, {
        method: "POST",
        body,
      });
      changed();
      notify("Opinia została dodana.");
      onClose();
    });
  }
  return (
    <Modal title="Jak oceniasz wizytę?" onClose={onClose}>
      <p className="section-copy">
        {appointment.doctorName} · {dateLabel(appointment.date)}
      </p>
      <form onSubmit={submit}>
        <fieldset className="rating-picker">
          <legend>Twoja ocena</legend>
          {[1, 2, 3, 4, 5].map((value) => (
            <label className={rating >= value ? "filled" : ""} key={value}>
              <input
                type="radio"
                name="rating"
                value={value}
                checked={rating === value}
                onChange={() => setRating(value)}
              />
              <Icon name="star" size={31} />
              <span className="sr-only">{value} na 5</span>
            </label>
          ))}
        </fieldset>
        <Textarea
          label="Twoja opinia"
          name="comment"
          rows={4}
          maxLength={3000}
          placeholder="Podziel się swoimi wrażeniami z konsultacji."
        />
        <ErrorBox>{mutation.error}</ErrorBox>
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Wróć
          </Button>
          <Button type="submit" busy={mutation.busy}>
            Opublikuj opinię
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export function ReviewList({ reviews }) {
  const [report, setReport] = useState(null);
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
            <button
              className="text-button muted small-copy"
              onClick={() => setReport(review)}
            >
              Zgłoś opinię
            </button>
          </article>
        ))}
      </div>
      {report && (
        <ReportModal review={report} onClose={() => setReport(null)} />
      )}
    </>
  );
}

function ReportModal({ review, onClose }) {
  const { notify, changed } = useApp(),
    mutation = useMutation();
  async function submit(event) {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    await mutation.run(async () => {
      await api(`/reviews/${review.id}/reports`, { method: "POST", body });
      changed();
      notify("Zgłoszenie zostało przekazane administratorowi.");
      onClose();
    });
  }
  return (
    <Modal title="Zgłoś opinię do moderacji" onClose={onClose}>
      <form onSubmit={submit}>
        <Select label="Powód zgłoszenia" name="category">
          <option value="inappropriate">Niestosowna treść</option>
          <option value="misleading">Wprowadzanie w błąd</option>
          <option value="privacy">Naruszenie prywatności</option>
          <option value="spam">Spam</option>
          <option value="other">Inny powód</option>
        </Select>
        <Textarea
          label="Uzasadnienie"
          name="reason"
          required
          minLength={5}
          maxLength={2000}
          rows={4}
        />
        <ErrorBox>{mutation.error}</ErrorBox>
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Wróć
          </Button>
          <Button type="submit" busy={mutation.busy}>
            Wyślij zgłoszenie
          </Button>
        </div>
      </form>
    </Modal>
  );
}
