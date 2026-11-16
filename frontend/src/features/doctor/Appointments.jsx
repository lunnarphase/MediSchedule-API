import { useState } from "react";
import { dateLabel, useData, api } from "../../api";
import {
  Badge,
  Empty,
  ErrorBox,
  Loading,
  PageHeading,
  RefreshButton,
  Button,
  Modal,
  Select,
  Textarea,
  useApp,
  useMutation,
} from "../../components";

export function DoctorAppointments() {
  const state = useData("/appointments", []);
  const [status, setStatus] = useState("all"),
    [search, setSearch] = useState("");
  const shown = (state.data || [])
    .filter(
      (a) =>
        (status === "all" || a.status === status) &&
        `${a.patientName} ${a.date}`
          .toLocaleLowerCase("pl")
          .includes(search.toLocaleLowerCase("pl")),
    )
    .sort((a, b) => `${b.date}${b.time}`.localeCompare(`${a.date}${a.time}`));
  return (
    <>
      <PageHeading
        title="Moje wizyty"
        description="Wizyty pacjentów, statusy konsultacji i zalecenia."
        actions={<RefreshButton onClick={state.refresh} />}
      />
      <div className="list-toolbar">
        <div className="filter-chips" aria-label="Filtruj wizyty">
          {[
            ["all", "Wszystkie"],
            ["confirmed", "Potwierdzone"],
            ["completed", "Zakończone"],
            ["cancelled", "Anulowane"],
            ["noshow", "Nieobecności"],
          ].map(([key, label]) => (
            <button
              key={key}
              className={status === key ? "selected" : ""}
              onClick={() => setStatus(key)}
            >
              {label}
            </button>
          ))}
        </div>
        <input
          className="search-input"
          aria-label="Szukaj pacjenta lub daty wizyty"
          placeholder="Szukaj pacjenta…"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>
      {state.loading && !state.data?.length ? (
        <Loading />
      ) : state.error ? (
        <ErrorBox retry={state.refresh}>{state.error}</ErrorBox>
      ) : shown.length ? (
        <DoctorAppointmentList appointments={shown} />
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

export function DoctorAppointmentList({ appointments }) {
  const [selected, setSelected] = useState(null);
  const groups = appointments.reduce((result, appointment) => {
    (result[appointment.date] ||= []).push(appointment);
    return result;
  }, {});
  return (
    <>
      <div className="appointment-groups">
        {Object.entries(groups).map(([date, visits]) => (
          <section key={date} className="appointment-day">
            <h3 className="day-heading">{dateLabel(date)}</h3>
            <div className="day-visits">
              {visits.map((appointment) => (
                <article className="appointment-card" key={appointment.id}>
                  <div className="appointment-time">
                    <strong>{appointment.time?.slice(0, 5)}</strong>
                    <span>do {appointment.endTime?.slice(0, 5)}</span>
                  </div>
                  <div className="appointment-details">
                    <h3>{appointment.patientName}</h3>
                    <Badge status={appointment.status} />
                    {appointment.recommendations && (
                      <div className="recommendations">
                        <strong>Zalecenia lekarza</strong>
                        <p>{appointment.recommendations}</p>
                      </div>
                    )}
                    {appointment.status === "cancelled" &&
                      appointment.cancelReason && (
                        <p className="muted small-copy">
                          Powód anulowania: {appointment.cancelReason}
                        </p>
                      )}
                  </div>
                  <div className="appointment-actions">
                    {appointment.status !== "cancelled" && (
                      <Button
                        variant="secondary"
                        onClick={() => setSelected(appointment)}
                      >
                        Status i zalecenia
                      </Button>
                    )}
                  </div>
                </article>
              ))}
            </div>
          </section>
        ))}
      </div>
      {selected && (
        <StatusModal appointment={selected} onClose={() => setSelected(null)} />
      )}
    </>
  );
}

function StatusModal({ appointment, onClose }) {
  const { changed, notify } = useApp(),
    mutation = useMutation();
  const finalStatus = appointment.status !== "confirmed";
  const statusLabels = {
    confirmed: "Potwierdzona",
    completed: "Zakończona",
    cancelled: "Anulowana",
    noshow: "Nieobecność pacjenta",
  };
  async function submit(event) {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    await mutation.run(async () => {
      await api(`/appointments/${appointment.id}/status`, {
        method: "PATCH",
        body,
      });
      changed();
      notify("Dane wizyty zostały zapisane.");
      onClose();
    });
  }
  return (
    <Modal title="Status wizyty i zalecenia" onClose={onClose}>
      <p className="section-copy">
        {appointment.patientName} · {dateLabel(appointment.date)} o{" "}
        {appointment.time?.slice(0, 5)}
      </p>
      <form onSubmit={submit}>
        <Select
          label="Status wizyty"
          name="status"
          defaultValue={appointment.status}
        >
          {(finalStatus
            ? [[appointment.status, statusLabels[appointment.status]]]
            : Object.entries(statusLabels)
          ).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        {finalStatus && (
          <p className="muted small-copy section-copy">
            Status tej wizyty jest końcowy i nie może zostać zmieniony. Możesz
            nadal uzupełnić lub poprawić zalecenia.
          </p>
        )}
        <Textarea
          label="Zalecenia po konsultacji"
          name="recommendations"
          defaultValue={appointment.recommendations || ""}
          rows={5}
          maxLength={5000}
        />
        <ErrorBox>{mutation.error}</ErrorBox>
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Wróć
          </Button>
          <Button type="submit" busy={mutation.busy}>
            Zapisz zmiany
          </Button>
        </div>
      </form>
    </Modal>
  );
}
