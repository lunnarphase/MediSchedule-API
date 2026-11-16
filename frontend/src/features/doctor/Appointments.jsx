import { useState } from "react";
import { dateLabel, useData } from "../../api";
import {
  Badge,
  Empty,
  ErrorBox,
  Loading,
  PageHeading,
  RefreshButton,
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
                </article>
              ))}
            </div>
          </section>
        ))}
      </div>
    </>
  );
}
