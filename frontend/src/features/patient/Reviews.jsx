import { useState } from "react";
import { api, dateLabel } from "../../api";
import {
  Button,
  ErrorBox,
  Icon,
  Modal,
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
