import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { api, resetCsrf } from "./api";
import { Brand, Button, Icon, Loading } from "./components";
import Auth from "./features/Auth";
import Shell from "./Shell";
import "./styles.css";

function App() {
  const [user, setUser] = useState(null);
  const [boot, setBoot] = useState(true);
  const [error, setError] = useState("");
  const [expired, setExpired] = useState(false);
  const [toast, setToast] = useState("");

  async function load() {
    setBoot(true);
    setError("");
    try {
      setUser(await api("/auth/me"));
    } catch (error) {
      setUser(null);
      if (error.status !== 401) setError(error.message);
    } finally {
      setBoot(false);
    }
  }

  useEffect(() => {
    load();
    const expire = () => {
      resetCsrf();
      setUser(null);
      setExpired(true);
      setToast("");
    };
    window.addEventListener("session-expired", expire);
    return () => window.removeEventListener("session-expired", expire);
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(""), 5000);
    return () => clearTimeout(timer);
  }, [toast]);

  const changed = () => window.dispatchEvent(new Event("data-changed"));
  const login = (account) => {
    resetCsrf();
    setUser(account);
    setExpired(false);
    location.hash = "/";
  };
  const logout = async () => {
    await api("/auth/logout", { method: "POST" });
    resetCsrf();
    setUser(null);
    setToast("");
    location.hash = "/";
  };

  if (boot)
    return (
      <div className="boot-screen">
        <Brand />
        <Loading label="Ładowanie aplikacji…" />
      </div>
    );
  if (error)
    return (
      <div className="boot-screen">
        <Brand />
        <h1>Nie udało się połączyć z aplikacją</h1>
        <p>{error}</p>
        <Button onClick={load}>Spróbuj ponownie</Button>
      </div>
    );

  return (
    <>
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main-content")?.focus();
        }}
      >
        Przejdź do treści
      </a>
      {user ? (
        <Shell
          user={user}
          setUser={setUser}
          onLogout={logout}
          notify={setToast}
          changed={changed}
        />
      ) : (
        <Auth onLogin={login} expired={expired} />
      )}
      <div className="toast-region" aria-live="polite">
        {toast && (
          <div className="toast">
            <Icon name="check" />
            {toast}
            <button
              className="icon-button"
              aria-label="Zamknij komunikat"
              onClick={() => setToast("")}
            >
              <Icon name="close" size={16} />
            </button>
          </div>
        )}
      </div>
    </>
  );
}

createRoot(document.getElementById("root")).render(<App />);
