import { api, resetCsrf } from "../api";
import { Brand, Button, ErrorBox, Field, useMutation } from "../components";

export default function Auth({ onLogin, expired = false }) {
  const mutation = useMutation();

  async function submit(event) {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    await mutation.run(async () => {
      const user = await api("/auth/login", { method: "POST", body });
      resetCsrf();
      onLogin(user);
    });
  }

  return (
    <main className="auth-page" id="main-content" tabIndex={-1}>
      <div className="auth-wrapper">
        <div className="auth-brand">
          <Brand />
          <p>System rezerwacji wizyt</p>
        </div>
        <section className="auth-card">
          <h1>Zaloguj się do konta</h1>
          {expired && <ErrorBox>Sesja wygasła. Zaloguj się ponownie.</ErrorBox>}
          <ErrorBox>{mutation.error}</ErrorBox>
          <form onSubmit={submit}>
            <Field
              label="Adres e-mail"
              name="email"
              type="email"
              required
              maxLength={255}
              autoComplete="email"
            />
            <Field
              label="Hasło"
              name="password"
              type="password"
              required
              maxLength={1024}
              autoComplete="current-password"
            />
            <Button type="submit" busy={mutation.busy} className="full-width">
              Zaloguj się
            </Button>
          </form>
        </section>
        <details className="demo-accounts">
          <summary>Konta demonstracyjne</summary>
          <dl>
            <dt>Pacjent</dt>
            <dd>patient@medischedule.local · Patient123!</dd>
            <dt>Lekarz</dt>
            <dd>doctor@medischedule.local · Doctor123!</dd>
            <dt>Administrator</dt>
            <dd>admin@medischedule.local · Admin123!</dd>
          </dl>
        </details>
      </div>
    </main>
  );
}
