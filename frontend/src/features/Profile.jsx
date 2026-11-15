import { PageHeading, useApp } from "../components";

export function Profile() {
  const { user } = useApp();
  return (
    <>
      <PageHeading title="Mój profil" />
      <section className="card">
        <h2>{user.name}</h2>
        <p>{user.email}</p>
        <p className="muted">
          {
            {
              patient: "Konto pacjenta",
              doctor: "Konto lekarza",
              admin: "Administrator",
            }[user.role]
          }
        </p>
      </section>
    </>
  );
}
