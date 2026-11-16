import { useEffect, useState } from "react";
import { AppContext, Brand, Confirm, Icon } from "./components";
import { Profile } from "./features/Profile";
import { Doctors } from "./features/patient/Doctors";
import { PatientDashboard } from "./features/patient/Dashboard";
import { Appointments } from "./features/patient/Appointments";
import { useData } from "./api";
import { Notifications } from "./features/Notifications";

import { DoctorDashboard } from "./features/doctor/Dashboard";
import { DoctorAppointments } from "./features/doctor/Appointments";
import { DoctorProfile } from "./features/doctor/Profile";
import { Schedule } from "./features/doctor/Schedule";

const patientMenu = [
  ["home", "Strona główna", "chart"],
  ["appointments", "Moje wizyty", "calendar"],
  ["doctors", "Nasi specjaliści", "users"],
];
const doctorMenu = [
  ["home", "Panel lekarza", "chart"],
  ["appointments", "Moje wizyty", "calendar"],
  ["schedule", "Mój grafik", "clock"],
  ["doctor-profile", "Profil lekarza", "users"],
];
const roles = { patient: "Pacjent", doctor: "Lekarz", admin: "Administrator" };

function defaultRoute(role) {
  return role === "patient" || role === "doctor" ? "home" : "profile";
}

function readRoute(role) {
  const route = location.hash.replace(/^#\/?/, "");
  const allowed = [
    "profile",
    "notifications",
    ...(role === "patient"
      ? patientMenu.map(([key]) => key)
      : role === "doctor"
        ? doctorMenu.map(([key]) => key)
        : []),
  ];
  return allowed.includes(route) ? route : defaultRoute(role);
}

export default function Shell({ user, setUser, onLogout, notify, changed }) {
  const [route, setRoute] = useState(() => readRoute(user.role));
  const [mobile, setMobile] = useState(false);
  const [logout, setLogout] = useState(false);
  const notifications = useData("/notifications", []);
  const unread = (notifications.data || []).filter(
    (notice) => !notice.read,
  ).length;

  useEffect(() => {
    const syncRoute = () => {
      const next = readRoute(user.role);
      if (location.hash !== `#/${next}`)
        history.replaceState(history.state, "", `#/${next}`);
      setRoute(next);
      setMobile(false);
      window.scrollTo({ top: 0 });
    };
    syncRoute();
    window.addEventListener("hashchange", syncRoute);
    return () => window.removeEventListener("hashchange", syncRoute);
  }, [user.role]);

  const navigate = (next) => {
    location.hash = `/${next}`;
    setMobile(false);
  };
  const views = {
    profile: <Profile />,
    ...(user.role === "patient" ? { doctors: <Doctors /> } : {}),
    ...(user.role === "patient"
      ? { home: <PatientDashboard />, appointments: <Appointments /> }
      : {}),
    notifications: <Notifications />,
    ...(user.role === "doctor"
      ? { home: <DoctorDashboard />, appointments: <DoctorAppointments /> }
      : {}),
    ...(user.role === "doctor" ? { "doctor-profile": <DoctorProfile /> } : {}),
    ...(user.role === "doctor" ? { schedule: <Schedule /> } : {}),
  };
  const items =
    user.role === "patient"
      ? patientMenu
      : user.role === "doctor"
        ? doctorMenu
        : [];

  return (
    <AppContext.Provider value={{ user, setUser, navigate, notify, changed }}>
      <div className="app-shell">
        <header className="app-header">
          <a
            href={`#/${defaultRoute(user.role)}`}
            className="brand-link"
            aria-label="MediSchedule — strona główna"
            onClick={(event) => {
              event.preventDefault();
              navigate(defaultRoute(user.role));
            }}
          >
            <Brand small />
          </a>
          <nav
            className={`main-nav ${mobile ? "is-open" : ""}`}
            aria-label="Menu główne"
          >
            {items.map(([key, label, icon]) => (
              <a
                key={key}
                href={`#/${key}`}
                className={route === key ? "active" : ""}
                aria-current={route === key ? "page" : undefined}
                onClick={(event) => {
                  event.preventDefault();
                  navigate(key);
                }}
              >
                <Icon name={icon} size={17} />
                {label}
              </a>
            ))}
            <button
              type="button"
              className="mobile-logout"
              onClick={() => {
                setMobile(false);
                setLogout(true);
              }}
            >
              <Icon name="logout" size={17} />
              Wyloguj się
            </button>
          </nav>
          <div className="header-actions">
            <button
              className={`icon-button notification-button ${route === "notifications" ? "active" : ""}`}
              onClick={() => navigate("notifications")}
              aria-label={`Powiadomienia${unread ? `, nieprzeczytane: ${unread}` : ""}`}
            >
              <Icon name="bell" />
              {unread > 0 && (
                <span className="unread-count">
                  {unread > 9 ? "9+" : unread}
                </span>
              )}
            </button>
            <button
              className="header-user"
              onClick={() => navigate("profile")}
              aria-label="Mój profil"
            >
              <span className="avatar">
                {user.name
                  ?.split(" ")
                  .map((word) => word[0])
                  .slice(0, 2)
                  .join("")}
              </span>
              <span className="header-user-name">
                {user.name}
                <small>{roles[user.role]}</small>
              </span>
            </button>
            <button
              className="icon-button logout-button"
              aria-label="Wyloguj się"
              onClick={() => setLogout(true)}
            >
              <Icon name="logout" />
            </button>
            <button
              className="icon-button mobile-menu"
              aria-label={mobile ? "Zwiń menu" : "Rozwiń menu"}
              aria-expanded={mobile}
              onClick={() => setMobile((value) => !value)}
            >
              <Icon name={mobile ? "close" : "menu"} />
            </button>
          </div>
        </header>
        <main className="main-content" id="main-content" tabIndex={-1}>
          {views[route] || views[defaultRoute(user.role)]}
        </main>
        <footer className="app-footer">
          <span>MediSchedule</span>
          <span>System rezerwacji wizyt</span>
        </footer>
        {logout && (
          <Confirm
            title="Wylogować się?"
            label="Wyloguj się"
            danger={false}
            onClose={() => setLogout(false)}
            onConfirm={onLogout}
          >
            Zakończysz bieżącą sesję w MediSchedule.
          </Confirm>
        )}
      </div>
    </AppContext.Provider>
  );
}
