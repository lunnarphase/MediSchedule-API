import { useCallback, useEffect, useRef, useState } from "react";
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

import { AdminDashboard } from "./features/admin/Dashboard";
import { AdminUsers } from "./features/admin/Users";
import { AdminAppointments } from "./features/admin/Appointments";
import { AdminReviews, AdminReports } from "./features/admin/Moderation";
import { AdminSecurity } from "./features/admin/Security";

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
const adminMenu = [
  ["home", "Panel administratora", "chart"],
  ["users", "Użytkownicy", "users"],
  ["appointments", "Wizyty", "calendar"],
  ["reviews", "Opinie", "star"],
  ["reports", "Zgłoszenia", "bell"],
  ["security", "Bezpieczeństwo", "shield"],
];
const roles = { patient: "Pacjent", doctor: "Lekarz", admin: "Administrator" };

function defaultRoute() {
  return "home";
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
        : role === "admin"
          ? adminMenu.map(([key]) => key)
          : []),
  ];
  return allowed.includes(route) ? route : defaultRoute();
}

export default function Shell({ user, setUser, onLogout, notify, changed }) {
  const [route, setRoute] = useState(() => readRoute(user.role)),
    [mobile, setMobile] = useState(false),
    [logout, setLogout] = useState(false),
    [pendingNavigation, setPendingNavigation] = useState(null);
  const activeRoute = useRef(route),
    navigationBlocked = useRef(false),
    historyIndex = useRef(history.state?.medischeduleIndex ?? 0),
    restoringNavigation = useRef(null);
  const setNavigationBlocked = useCallback((blocked) => {
    navigationBlocked.current = blocked;
  }, []);
  const notifications = useData("/notifications", []);
  function applyRoute(next) {
    activeRoute.current = next;
    setRoute(next);
    setMobile(false);
    window.scrollTo({ top: 0 });
  }
  function commitNavigation(next) {
    if (next === activeRoute.current) {
      setMobile(false);
      return;
    }
    historyIndex.current += 1;
    history.pushState(
      { ...history.state, medischeduleIndex: historyIndex.current },
      "",
      `#/${next}`,
    );
    applyRoute(next);
  }
  useEffect(() => {
    history.replaceState(
      { ...history.state, medischeduleIndex: historyIndex.current },
      "",
      `#/${activeRoute.current}`,
    );
    const routeChange = () => {
      const next = readRoute(user.role);
      const targetIndex = history.state?.medischeduleIndex;
      if (restoringNavigation.current) {
        if (next === activeRoute.current) {
          const pending = restoringNavigation.current;
          restoringNavigation.current = null;
          setPendingNavigation(pending);
        }
        return;
      }
      if (next === activeRoute.current) {
        if (location.hash !== `#/${next}`)
          history.replaceState(history.state, "", `#/${next}`);
        return;
      }
      const delta =
        Number.isInteger(targetIndex) && targetIndex !== historyIndex.current
          ? targetIndex - historyIndex.current
          : null;
      if (navigationBlocked.current) {
        const pending = { next, historyDelta: delta };
        if (delta !== null) {
          restoringNavigation.current = pending;
          history.go(-delta);
        } else {
          history.replaceState(
            { ...history.state, medischeduleIndex: historyIndex.current },
            "",
            `#/${activeRoute.current}`,
          );
          setPendingNavigation(pending);
        }
        return;
      }
      if (delta !== null) historyIndex.current = targetIndex;
      else {
        historyIndex.current += 1;
        history.replaceState(
          { ...history.state, medischeduleIndex: historyIndex.current },
          "",
          location.href,
        );
      }
      applyRoute(next);
    };
    const beforeUnload = (event) => {
      if (!navigationBlocked.current) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("popstate", routeChange);
    window.addEventListener("hashchange", routeChange);
    window.addEventListener("beforeunload", beforeUnload);
    return () => {
      window.removeEventListener("popstate", routeChange);
      window.removeEventListener("hashchange", routeChange);
      window.removeEventListener("beforeunload", beforeUnload);
    };
  }, []);
  const navigate = (next) => {
    if (navigationBlocked.current && next !== activeRoute.current) {
      setPendingNavigation({ next });
      setMobile(false);
    } else commitNavigation(next);
  };
  const unread = (notifications.data || []).filter(
    (notice) => !notice.read,
  ).length;
  const views = {
    profile: <Profile />,
    ...(user.role === "patient" ? { doctors: <Doctors /> } : {}),
    ...(user.role === "patient"
      ? { home: <PatientDashboard />, appointments: <Appointments /> }
      : {}),
    notifications: <Notifications />,
    ...(user.role === "admin" ? { home: <AdminDashboard /> } : {}),
    ...(user.role === "admin" ? { users: <AdminUsers /> } : {}),
    ...(user.role === "admin" ? { appointments: <AdminAppointments /> } : {}),
    ...(user.role === "admin" ? { reviews: <AdminReviews /> } : {}),
    ...(user.role === "admin" ? { reports: <AdminReports /> } : {}),
    ...(user.role === "admin" ? { security: <AdminSecurity /> } : {}),
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
        : user.role === "admin"
          ? adminMenu
          : [];

  return (
    <AppContext.Provider
      value={{ user, setUser, navigate, notify, changed, setNavigationBlocked }}
    >
      <div className="app-shell">
        <header className="app-header">
          <a
            href={`#/${defaultRoute()}`}
            className="brand-link"
            aria-label="MediSchedule — strona główna"
            onClick={(event) => {
              event.preventDefault();
              navigate(defaultRoute());
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
          {views[route] || views[defaultRoute()]}
        </main>
        <footer className="app-footer">
          <span>MediSchedule</span>
          <span>System rezerwacji wizyt</span>
        </footer>
        {pendingNavigation && (
          <Confirm
            title="Pominąć niezapisane zmiany?"
            label="Opuść grafik"
            onClose={() => setPendingNavigation(null)}
            onConfirm={async () => {
              const pending = pendingNavigation;
              navigationBlocked.current = false;
              if (pending.historyDelta != null)
                history.go(pending.historyDelta);
              else commitNavigation(pending.next);
            }}
          >
            Masz niezapisane zmiany w grafiku. Opuść widok, aby je odrzucić,
            albo wybierz „Wróć”, aby kontynuować edycję.
          </Confirm>
        )}
        {logout && (
          <Confirm
            title="Wylogować się?"
            label="Wyloguj się"
            danger={false}
            onClose={() => setLogout(false)}
            onConfirm={onLogout}
          >
            {navigationBlocked.current
              ? "Masz niezapisane zmiany w grafiku. Wylogowanie spowoduje ich utratę."
              : "Zakończysz bieżącą sesję w MediSchedule."}
          </Confirm>
        )}
      </div>
    </AppContext.Provider>
  );
}
