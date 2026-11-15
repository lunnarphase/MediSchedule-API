import { useState } from "react";
import { api, dateLabel, isUpcoming, useData } from "../../api";
import {
  Badge,
  Button,
  Empty,
  ErrorBox,
  Icon,
  Loading,
  Modal,
  PageHeading,
  RefreshButton,
  Textarea,
  useApp,
  useMutation,
} from "../../components";
import { ReviewModal } from "./Reviews";

export function Appointments() {
  const state = useData("/appointments", []);
  const [status, setStatus] = useState("all"),
    [search, setSearch] = useState("");
  const shown = (state.data || [])
    .filter(
      (a) =>
        (status === "all" ||
          (status === "confirmed" ? isUpcoming(a) : a.status === status)) &&
        `${a.doctorName} ${a.patientName} ${a.date}`
          .toLocaleLowerCase("pl")
          .includes(search.toLocaleLowerCase("pl")),
    )
    .sort((a, b) => `${b.date}${b.time}`.localeCompare(`${a.date}${a.time}`));
  return (
    <>
      <PageHeading
        title="Moje wizyty"
        description="Nadchodzące konsultacje, historia wizyt i zalecenia lekarzy."
        actions={<RefreshButton onClick={state.refresh} />}
      />
      <div className="list-toolbar">
        <div className="filter-chips" aria-label="Filtruj wizyty">
          {[
            ["all", "Wszystkie"],
            ["confirmed", "Nadchodzące"],
            ["completed", "Zakończone"],
            ["cancelled", "Anulowane"],
            ["noshow", "Nieobecności"],
          ].map(([key, label]) => (
            <button
              className={status === key ? "selected" : ""}
              key={key}
              onClick={() => setStatus(key)}
            >
              {label}
            </button>
          ))}
        </div>
        <input
          className="search-input"
          aria-label="Szukaj w wizytach"
          placeholder="Szukaj lekarza…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>
      {state.loading && !state.data?.length ? (
        <Loading />
      ) : state.error ? (
        <ErrorBox retry={state.refresh}>{state.error}</ErrorBox>
      ) : shown.length ? (
        <AppointmentList appointments={shown} />
      ) : (
        <section className="card">
          <Empty title="Brak wizyt">
            Wizyty pasujące do wybranego filtra pojawią się tutaj.
          </Empty>
        </section>
      )}
    </>
  );
}

export function AppointmentList({ appointments }) {
  const [action, setAction] = useState(null);
  const groups = appointments.reduce((result, a) => {
    (result[a.date] ||= []).push(a);
    return result;
  }, {});
  return (
    <>
      <div className="appointment-groups">
        {Object.entries(groups).map(([date, visits]) => (
          <section key={date} className="appointment-day">
            <h3 className="day-heading">{dateLabel(date)}</h3>
            <div className="day-visits">
              {visits.map((a) => (
                <article className="appointment-card" key={a.id}>
                  <div className="appointment-time">
                    <strong>{a.time?.slice(0, 5)}</strong>
                    <span>
                      {a.endTime
                        ? `do ${a.endTime.slice(0, 5)}`
                        : "Konsultacja"}
                    </span>
                  </div>
                  <div className="appointment-details">
                    <h3>{a.doctorName}</h3>
                    <Badge status={a.status} />
                    {a.recommendations && (
                      <div className="recommendations">
                        <strong>Zalecenia lekarza</strong>
                        <p>{a.recommendations}</p>
                      </div>
                    )}
                    {a.status === "cancelled" && a.cancelReason && (
                      <p className="muted small-copy">
                        Powód anulowania: {a.cancelReason}
                      </p>
                    )}
                  </div>
                  <div className="appointment-actions">
                    {a.status === "confirmed" && (
                      <Button
                        variant="secondary"
                        onClick={() =>
                          setAction({ type: "cancel", appointment: a })
                        }
                      >
                        Anuluj wizytę
                      </Button>
                    )}
                    {a.status === "completed" && !a.reviewSubmitted && (
                      <Button
                        variant="review"
                        onClick={() =>
                          setAction({ type: "review", appointment: a })
                        }
                      >
                        <Icon name="star" size={16} />
                        Wystaw opinię
                      </Button>
                    )}
                    {a.reviewSubmitted && (
                      <span className="small-copy muted">
                        <Icon name="check" size={14} /> Opinia wystawiona
                      </span>
                    )}
                  </div>
                </article>
              ))}
            </div>
          </section>
        ))}
      </div>
      {action?.type === "cancel" && (
        <CancelModal
          appointment={action.appointment}
          onClose={() => setAction(null)}
        />
      )}{" "}
      {action?.type === "review" && (
        <ReviewModal
          appointment={action.appointment}
          onClose={() => setAction(null)}
        />
      )}{" "}
    </>
  );
}

function CancelModal({ appointment, onClose }) {
  const { changed, notify } = useApp(),
    mutation = useMutation();
  async function submit(event) {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    await mutation.run(async () => {
      await api(`/appointments/${appointment.id}/cancel`, {
        method: "PATCH",
        body,
      });
      changed();
      notify("Wizyta została anulowana.");
      onClose();
    });
  }
  return (
    <Modal title="Anuluj wizytę" onClose={onClose}>
      <p className="section-copy">
        {appointment.doctorName} · {dateLabel(appointment.date)} o{" "}
        {appointment.time?.slice(0, 5)}
      </p>
      <form onSubmit={submit}>
        <Textarea
          label="Powód anulowania"
          name="reason"
          required
          maxLength={1000}
          rows={3}
          placeholder="Podaj krótki powód anulowania wizyty."
        />
        <ErrorBox>{mutation.error}</ErrorBox>
        <div className="form-actions">
          <Button variant="secondary" type="button" onClick={onClose}>
            Zachowaj wizytę
          </Button>
          <Button type="submit" variant="danger" busy={mutation.busy}>
            Anuluj wizytę
          </Button>
        </div>
      </form>
    </Modal>
  );
}
