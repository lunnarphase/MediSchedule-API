import { localDate, useData } from "../../api";
import {
  Badge,
  DataState,
  ErrorBox,
  Icon,
  PageHeading,
  RefreshButton,
  Stat,
  useApp,
} from "../../components";

export function AdminDashboard() {
  const users = useData("/admin/users", []);
  const appointments = useData("/admin/appointments", []);
  const reports = useData("/admin/reports", []);
  const { navigate } = useApp();
  const pending = (reports.data || []).filter(
    (report) => report.status === "pending",
  ).length;
  const today = (appointments.data || [])
    .filter(
      (appointment) =>
        appointment.date === localDate() && appointment.status !== "cancelled",
    )
    .sort((a, b) => a.time.localeCompare(b.time));
  const doctors = (users.data || []).filter(
    (user) => user.role === "doctor" && !user.blocked,
  ).length;
  const shortcuts = [
    ["users", "Użytkownicy", "Konta i uprawnienia użytkowników.", "users"],
    [
      "appointments",
      "Wizyty",
      "Terminy, pacjenci i statusy rezerwacji.",
      "calendar",
    ],
    [
      "reports",
      "Zgłoszenia opinii",
      "Zgłoszenia i decyzje moderacyjne.",
      "star",
    ],
  ];
  function refresh() {
    users.refresh();
    appointments.refresh();
    reports.refresh();
  }
  return (
    <>
      <PageHeading
        title="Panel administratora"
        description="Użytkownicy, wizyty i zgłoszenia."
        actions={<RefreshButton onClick={refresh} />}
      />
      <ErrorBox retry={users.refresh}>{users.error}</ErrorBox>
      <div className="stats-grid four">
        <Stat
          icon="users"
          title="Użytkownicy"
          value={users.data?.length || 0}
        />
        <Stat icon="calendar" title="Wizyty dzisiaj" value={today.length} />
        <Stat
          icon="shield"
          title="Oczekujące zgłoszenia"
          value={pending}
          tone="amber"
        />
        <Stat icon="user" title="Aktywni lekarze" value={doctors} />
      </div>
      {shortcuts.length > 0 && (
        <div className="admin-shortcuts">
          {shortcuts.map(([page, title, description, icon]) => (
            <button
              className="card shortcut-card"
              key={page}
              onClick={() => navigate(page)}
            >
              <span className="shortcut-icon">
                <Icon name={icon} size={26} />
              </span>
              <h2>{title}</h2>
              <p>{description}</p>
              <span className="shortcut-arrow">
                <Icon name="chevron" size={16} />
              </span>
            </button>
          ))}
        </div>
      )}
      <div className="two-columns">
        <section className="card">
          <div className="section-heading">
            <h2>Dzisiejsze wizyty</h2>
            <button
              className="text-button"
              onClick={() => navigate("appointments")}
            >
              Wszystkie wizyty →
            </button>
          </div>
          <DataState state={appointments} empty="Brak wizyt">
            {() =>
              today.length ? (
                <div className="compact-visits">
                  {today.slice(0, 8).map((appointment) => (
                    <div className="compact-visit" key={appointment.id}>
                      <strong>{appointment.time?.slice(0, 5)}</strong>
                      <div>
                        <strong>{appointment.patientName}</strong>
                        <small>{appointment.doctorName}</small>
                      </div>
                      <Badge status={appointment.status} />
                    </div>
                  ))}
                </div>
              ) : (
                <p className="muted">Dzisiaj nie ma zaplanowanych wizyt.</p>
              )
            }
          </DataState>
        </section>
        <section className="card">
          <h2>Zgłoszenia opinii</h2>
          <DataState state={reports} empty="Brak zgłoszeń">
            {() => (
              <div className="moderation-summary">
                <span className="large-number">{pending}</span>
                <p>
                  {pending
                    ? "Zgłoszenia oczekujące na rozpatrzenie."
                    : "Wszystkie zgłoszenia zostały rozpatrzone."}
                </p>
              </div>
            )}
          </DataState>
          <button className="text-button" onClick={() => navigate("reports")}>
            Przejdź do zgłoszeń →
          </button>
        </section>
      </div>
    </>
  );
}
