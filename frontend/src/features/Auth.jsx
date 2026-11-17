import { useState } from "react";
import { api, resetCsrf, validPhone } from "../api";
import {
  Brand,
  Button,
  ErrorBox,
  Field,
  SuccessBox,
  useMutation,
} from "../components";

export default function Auth({ onLogin, expired = false }) {
  const [register, setRegister] = useState(false),
    [registered, setRegistered] = useState(false);
  const mutation = useMutation();
  async function submit(event) {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    const rememberEmail = body.rememberEmail;
    delete body.rememberEmail;
    if (register && !validPhone(body.phone)) {
      mutation.setError("Telefon może zawierać cyfry, spacje i znaki + ( ) -.");
      return;
    }
    await mutation.run(async () => {
      if (register) {
        await api("/auth/register", { method: "POST", body });
        resetCsrf();
        setRegister(false);
        setRegistered(true);
      } else {
        const user = await api("/auth/login", { method: "POST", body });
        if (rememberEmail)
          localStorage.setItem("medischedule-email", body.email);
        else localStorage.removeItem("medischedule-email");
        resetCsrf();
        onLogin(user);
      }
    });
  }
  return (
    <main
      className={`auth-page ${register ? "register-page" : ""}`}
      id="main-content"
      tabIndex={-1}
    >
      <div className="auth-wrapper">
        <div className="auth-brand">
          <Brand />
          <p>System rezerwacji wizyt</p>
        </div>
        <section className="auth-card">
          <h1>{register ? "Utwórz konto pacjenta" : "Zaloguj się do konta"}</h1>
          <SuccessBox>
            {registered
              ? "Konto zostało utworzone. Możesz się teraz zalogować."
              : ""}
          </SuccessBox>
          {expired && <ErrorBox>Sesja wygasła. Zaloguj się ponownie.</ErrorBox>}
          <ErrorBox>{mutation.error}</ErrorBox>
          <form onSubmit={submit} key={String(register)}>
            {register && (
              <Field
                label="Imię i nazwisko"
                name="name"
                required
                minLength={3}
                maxLength={100}
                autoComplete="name"
                placeholder="Jan Kowalski"
              />
            )}
            <Field
              label="Adres e-mail"
              name="email"
              type="email"
              required
              maxLength={255}
              autoComplete="email"
              placeholder="twoj@email.pl"
              defaultValue={
                register ? "" : localStorage.getItem("medischedule-email") || ""
              }
            />
            {register && (
              <div className="form-grid">
                <Field
                  label="PESEL"
                  name="pesel"
                  inputMode="numeric"
                  pattern="[0-9]{11}"
                  minLength={11}
                  maxLength={11}
                  required
                  hint="11 cyfr"
                />
                <Field
                  label="Telefon"
                  name="phone"
                  type="tel"
                  maxLength={20}
                  autoComplete="tel"
                />
              </div>
            )}
            <Field
              label="Hasło"
              name="password"
              type="password"
              required
              minLength={register ? 8 : 1}
              maxLength={register ? 72 : 1024}
              autoComplete={register ? "new-password" : "current-password"}
              placeholder="Wpisz hasło"
              hint={
                register
                  ? "Minimum 8 znaków, mała i wielka litera, cyfra oraz znak specjalny."
                  : undefined
              }
            />
            {!register && (
              <label className="remember-email">
                <input
                  type="checkbox"
                  name="rememberEmail"
                  defaultChecked={Boolean(
                    localStorage.getItem("medischedule-email"),
                  )}
                />
                Zapamiętaj mój e-mail
              </label>
            )}
            <Button type="submit" busy={mutation.busy} className="full-width">
              {register ? "Utwórz konto" : "Zaloguj się"}
              <span className="button-arrow">→</span>
            </Button>
          </form>
        </section>
        <p className="auth-switch">
          {register ? "Masz już konto?" : "Nie masz jeszcze konta?"}{" "}
          <button
            className="text-button"
            onClick={() => {
              setRegister((v) => !v);
              mutation.setError("");
              setRegistered(false);
            }}
          >
            {register ? "Zaloguj się" : "Zarejestruj się"}
          </button>
        </p>
        <footer className="auth-footer">
          MediSchedule · System rezerwacji wizyt medycznych
        </footer>
      </div>
    </main>
  );
}
