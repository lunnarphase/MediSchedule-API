import { useState } from "react";
import { isUpcoming, useData } from "../../api";
import {
  Button,
  DataState,
  Empty,
  ErrorBox,
  Field,
  Icon,
  Loading,
  PageHeading,
  Select,
  Stat,
  useApp,
} from "../../components";
import { DoctorCard, DoctorModal } from "./Doctors";
import { AppointmentList } from "./Appointments";

export function PatientDashboard() {
  const { user, navigate } = useApp();
  const appointments = useData("/appointments", []),
    doctors = useData("/doctors", []),
    specializations = useData("/specializations", []);
  const [selected, setSelected] = useState(null);
  const upcoming = (appointments.data || [])
    .filter((appointment) => isUpcoming(appointment))
    .sort((a, b) => `${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`));
  const needsReview = (appointments.data || []).filter(
    (a) => a.status === "completed" && !a.reviewSubmitted,
  );
  const shown = [...upcoming, ...needsReview].slice(0, 4);
  function search(event) {
    event.preventDefault();
    sessionStorage.setItem(
      "doctor-search",
      JSON.stringify(Object.fromEntries(new FormData(event.currentTarget))),
    );
    navigate("doctors");
  }
  return (
    <>
      <PageHeading
        title={`Witaj ponownie, ${user.name?.split(" ")[0] || ""}`}
        description="Zarządzaj swoim zdrowiem w jednym miejscu."
      />
      <div className="stats-grid">
        <Stat
          icon="calendar"
          title="Nadchodzące wizyty"
          value={upcoming.length}
        />
        <Stat
          icon="check"
          title="Zakończone konsultacje"
          value={
            (appointments.data || []).filter((a) => a.status === "completed")
              .length
          }
        />
        <Stat
          icon="star"
          title="Wizyty do oceny"
          value={needsReview.length}
          tone="amber"
        />
      </div>
      <div className="dashboard-columns">
        <section>
          <div className="section-heading">
            <h2>Twoje wizyty</h2>
            <button
              className="text-button"
              onClick={() => navigate("appointments")}
            >
              Zobacz wszystkie <span>→</span>
            </button>
          </div>
          {appointments.loading ? (
            <Loading />
          ) : appointments.error ? (
            <ErrorBox retry={appointments.refresh}>
              {appointments.error}
            </ErrorBox>
          ) : shown.length ? (
            <AppointmentList appointments={shown} />
          ) : (
            <section className="card">
              <Empty title="Czas zadbać o siebie">
                Nie masz nadchodzących wizyt. Znajdź specjalistę i wybierz
                dogodny termin.
              </Empty>
              <Button
                className="full-width"
                onClick={() => navigate("doctors")}
              >
                Znajdź lekarza
              </Button>
            </section>
          )}
        </section>
        <aside className="dashboard-aside">
          <section className="card">
            <div className="card-title-icon">
              <span>
                <Icon name="search" />
              </span>
              <h2>Znajdź lekarza</h2>
            </div>
            <form onSubmit={search}>
              <Field
                label="Szukaj po nazwisku"
                name="q"
                placeholder="Wpisz nazwisko lekarza"
              />
              <Select label="Specjalizacja" name="specialization">
                <option value="">Wszystkie specjalizacje</option>
                {specializations.data?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
              <Button className="full-width" type="submit">
                Szukaj terminu <Icon name="chevron" size={17} />
              </Button>
            </form>
          </section>
          <section className="card suggested-doctors">
            <h2>Poznaj naszych specjalistów</h2>
            <DataState state={doctors} empty="Brak lekarzy">
              {(data) =>
                data
                  .slice(0, 3)
                  .map((doctor) => (
                    <DoctorCard
                      key={doctor.id}
                      doctor={doctor}
                      onSelect={setSelected}
                      compact
                    />
                  ))
              }
            </DataState>
          </section>
        </aside>
      </div>
      {selected && (
        <DoctorModal doctor={selected} onClose={() => setSelected(null)} />
      )}
    </>
  );
}
