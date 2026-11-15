import { useState } from "react";
import { api, useData, validPhone } from "../api";
import {
  Button,
  DataState,
  ErrorBox,
  Field,
  Icon,
  PageHeading,
  SuccessBox,
  useApp,
  useMutation,
} from "../components";

export function Profile() {
  const { user, setUser, notify, changed } = useApp();
  const profile = useData("/profile");
  const mutation = useMutation(),
    passwordMutation = useMutation();
  const [passwordSaved, setPasswordSaved] = useState(false),
    [profileSaved, setProfileSaved] = useState(false);
  async function save(event) {
    event.preventDefault();
    setProfileSaved(false);
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const body = {
      name: values.name,
      email: values.email,
      ...(user.role === "patient" ? { phone: values.phone || "" } : {}),
    };
    if (user.role === "patient" && !validPhone(body.phone)) {
      mutation.setError("Telefon może zawierać cyfry, spacje i znaki + ( ) -.");
      return;
    }
    await mutation.run(async () => {
      const saved = await api("/profile", { method: "PUT", body });
      setUser(saved || { ...user, ...body });
      setProfileSaved(true);
      notify("Dane profilu zostały zapisane.");
      changed();
    });
  }
  async function password(event) {
    event.preventDefault();
    setPasswordSaved(false);
    const form = event.currentTarget;
    const data = Object.fromEntries(new FormData(form));
    if (data.newPassword !== data.confirmPassword) {
      passwordMutation.setError("Nowe hasła muszą być takie same.");
      return;
    }
    await passwordMutation.run(async () => {
      await api("/profile/password", {
        method: "PUT",
        body: {
          currentPassword: data.currentPassword,
          newPassword: data.newPassword,
        },
      });
      form.reset();
      setPasswordSaved(true);
      notify("Hasło zostało zmienione.");
    });
  }
  return (
    <>
      <PageHeading
        title="Mój profil"
        description="Zadbaj o aktualność danych i bezpieczeństwo konta."
      />
      <div className="two-columns">
        <section className="card">
          <h2>Dane osobowe</h2>
          <DataState state={profile}>
            {(data) => (
              <form
                onSubmit={save}
                onChange={() => setProfileSaved(false)}
                key={`${data?.email}-${data?.name}`}
              >
                <div className="profile-intro">
                  <span className="avatar large">
                    {user.name
                      ?.split(" ")
                      .map((v) => v[0])
                      .slice(0, 2)
                      .join("")}
                  </span>
                  <div>
                    <strong>{user.name}</strong>
                    <p className="muted">
                      {
                        {
                          patient: "Konto pacjenta",
                          doctor: "Konto lekarza",
                          admin: "Administrator",
                        }[user.role]
                      }
                    </p>
                  </div>
                </div>
                <Field
                  label="Imię i nazwisko"
                  name="name"
                  required
                  minLength={3}
                  maxLength={100}
                  defaultValue={data?.name}
                />
                <Field
                  label="Adres e-mail"
                  type="email"
                  name="email"
                  required
                  maxLength={255}
                  defaultValue={data?.email}
                />
                {user.role === "patient" && (
                  <Field
                    label="Telefon"
                    type="tel"
                    name="phone"
                    maxLength={20}
                    defaultValue={data?.phone || ""}
                  />
                )}
                {data?.pesel && (
                  <Field
                    label="PESEL"
                    value={data.pesel}
                    readOnly
                    hint="Numer PESEL jest dostępny tylko do odczytu."
                  />
                )}
                <ErrorBox>{mutation.error}</ErrorBox>
                {profileSaved && (
                  <SuccessBox>
                    <Icon name="check" size={20} />
                    <span>Dane profilu zostały zapisane.</span>
                  </SuccessBox>
                )}
                <Button type="submit" busy={mutation.busy}>
                  Zapisz dane
                </Button>
              </form>
            )}
          </DataState>
        </section>
        <section className="card">
          <h2>Zmień hasło</h2>
          <p className="muted section-copy">
            Użyj silnego hasła, którego nie używasz w innych serwisach.
          </p>
          <form onSubmit={password} onChange={() => setPasswordSaved(false)}>
            <Field
              label="Obecne hasło"
              name="currentPassword"
              type="password"
              required
              autoComplete="current-password"
            />
            <Field
              label="Nowe hasło"
              name="newPassword"
              type="password"
              required
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
              hint="Minimum 8 znaków, mała i wielka litera, cyfra oraz znak specjalny."
            />
            <Field
              label="Powtórz nowe hasło"
              name="confirmPassword"
              type="password"
              required
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
            />
            <ErrorBox>{passwordMutation.error}</ErrorBox>
            {passwordSaved && (
              <SuccessBox>
                <Icon name="check" size={20} />
                <span>
                  Hasło zostało zmienione. Przy następnym logowaniu użyj nowego
                  hasła.
                </span>
              </SuccessBox>
            )}
            <Button type="submit" busy={passwordMutation.busy}>
              Zmień hasło
            </Button>
          </form>
        </section>
      </div>
    </>
  );
}
