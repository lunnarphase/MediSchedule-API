import { isUpcoming, localDate, useData } from "../../api";
import {
  DataState,
  Empty,
  ErrorBox,
  Loading,
  PageHeading,
  RefreshButton,
  Stat,
  useApp,
  Button,
  Icon,
} from "../../components";
import { DoctorAppointmentList } from "./Appointments";
import { ReviewList } from "../ReviewList";

export function DoctorDashboard() {
  const { user, navigate } = useApp();
  const appointments = useData("/appointments", []),
    reviews = useData("/doctor/reviews", []);
  const today = localDate();
  const todayVisits = (appointments.data || [])
    .filter((a) => a.date === today && a.status !== "cancelled")
    .sort((a, b) => a.time.localeCompare(b.time));
  const upcoming = (appointments.data || []).filter((appointment) =>
    isUpcoming(appointment),
  );
  const nextVisits = upcoming
    .filter((a) => a.date > today)
    .sort((a, b) => `${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`))
    .slice(0, 3);
  const average = reviews.data?.length
    ? reviews.data.reduce((sum, r) => sum + r.rating, 0) / reviews.data.length
    : 0;
  return (
    <>
      <PageHeading
        title={`Dzień dobry, ${user.name}`}
        description="Sprawdź dzisiejsze konsultacje i zaplanuj kolejny tydzień."
        actions={
          <Button variant="secondary" onClick={() => navigate("schedule")}>
            <Icon name="calendar" size={17} />
            Zarządzaj grafikiem
          </Button>
        }
      />
      <div className="stats-grid">
        <Stat
          icon="calendar"
          title="Dzisiejsze wizyty"
          value={todayVisits.length}
        />
        <Stat
          icon="clock"
          title="Nadchodzące konsultacje"
          value={upcoming.length}
        />
        <Stat
          icon="star"
          title="Średnia ocena pacjentów"
          value={average.toFixed(1)}
          tone="amber"
        />
      </div>
      <div className="dashboard-columns">
        <section>
          <div className="section-heading">
            <h2>Dzisiaj w Twoim gabinecie</h2>
            <RefreshButton onClick={appointments.refresh} />
          </div>
          {appointments.loading ? (
            <Loading />
          ) : appointments.error ? (
            <ErrorBox retry={appointments.refresh}>
              {appointments.error}
            </ErrorBox>
          ) : todayVisits.length ? (
            <DoctorAppointmentList appointments={todayVisits} />
          ) : (
            <section className="card">
              <Empty title="Dzisiaj bez zaplanowanych wizyt">
                Zarządzaj dostępnością, aby pacjenci mogli rezerwować kolejne
                konsultacje.
              </Empty>
              <Button
                variant="secondary"
                className="full-width"
                onClick={() => navigate("schedule")}
              >
                Przejdź do grafiku
              </Button>
            </section>
          )}
          <div className="section-heading spaced">
            <h2>Najbliższe konsultacje</h2>
            <button
              className="text-button"
              onClick={() => navigate("appointments")}
            >
              Wszystkie wizyty →
            </button>
          </div>
          {nextVisits.length ? (
            <DoctorAppointmentList appointments={nextVisits} />
          ) : (
            <p className="muted">Brak nadchodzących konsultacji.</p>
          )}
        </section>
        <aside className="dashboard-aside">
          <section className="card">
            <h2>Ostatnie opinie</h2>
            <DataState
              state={reviews}
              empty="Pacjenci nie wystawili jeszcze opinii"
            >
              {(data) => <ReviewList reviews={data.slice(0, 3)} />}
            </DataState>
          </section>
        </aside>
      </div>
    </>
  );
}
