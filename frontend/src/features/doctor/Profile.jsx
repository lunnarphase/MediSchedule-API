import { useState } from "react";
import { api, useData } from "../../api";
import {
  Button,
  DataState,
  ErrorBox,
  Field,
  PageHeading,
  SuccessBox,
  Textarea,
  useApp,
  useMutation,
} from "../../components";
import { ReviewList } from "../ReviewList";

export function DoctorProfile() {
  const [saved, setSaved] = useState(false);
  const profile = useData("/doctor/profile"),
    reviews = useData("/doctor/reviews", []),
    mutation = useMutation(),
    { notify, changed } = useApp();
  async function submit(event) {
    event.preventDefault();
    setSaved(false);
    const body = Object.fromEntries(new FormData(event.currentTarget));
    body.visitPrice = Number(body.visitPrice);
    body.visitDuration = Number(body.visitDuration);
    await mutation.run(async () => {
      await api("/doctor/profile", { method: "PUT", body });
      setSaved(true);
      changed();
      notify("Profil lekarza został zaktualizowany.");
    });
  }
  return (
    <>
      <PageHeading
        title="Profil lekarza"
        description="Przedstaw się pacjentom i ustal parametry konsultacji."
      />
      <div className="two-columns">
        <section className="card">
          <h2>Informacje o konsultacji</h2>
          <DataState state={profile}>
            {(data) => (
              <form
                onSubmit={submit}
                onChange={() => setSaved(false)}
                key={`${data?.visitDuration}-${data?.visitPrice}-${data?.bio}`}
              >
                <p className="muted section-copy">
                  {data?.specializations?.join(" · ")}
                </p>
                <Textarea
                  label="O mnie"
                  name="bio"
                  rows={7}
                  maxLength={5000}
                  defaultValue={data?.bio || ""}
                  placeholder="Opisz doświadczenie i zakres konsultacji."
                />
                <div className="form-grid">
                  <Field
                    label="Cena wizyty (zł)"
                    name="visitPrice"
                    type="number"
                    step="0.01"
                    min="0"
                    max="100000"
                    required
                    defaultValue={data?.visitPrice || 0}
                  />
                  <Field
                    label="Czas wizyty (minuty)"
                    name="visitDuration"
                    type="number"
                    min="10"
                    max="240"
                    required
                    defaultValue={data?.visitDuration || 30}
                  />
                </div>
                <p className="small-copy muted section-copy">
                  Nowy czas trwania dotyczy kolejnych rezerwacji. Dotychczasowe
                  wizyty zachowują swój czas.
                </p>
                <ErrorBox>{mutation.error}</ErrorBox>
                <SuccessBox>
                  {saved ? "Profil lekarza został zapisany." : ""}
                </SuccessBox>
                <Button type="submit" busy={mutation.busy}>
                  Zapisz profil
                </Button>
              </form>
            )}
          </DataState>
        </section>
        <section className="card">
          <div className="section-heading">
            <h2>Opinie pacjentów</h2>
            <span className="count">{reviews.data?.length || 0}</span>
          </div>
          <DataState state={reviews} empty="Nie masz jeszcze opinii">
            {(data) => <ReviewList reviews={data} />}
          </DataState>
        </section>
      </div>
    </>
  );
}
