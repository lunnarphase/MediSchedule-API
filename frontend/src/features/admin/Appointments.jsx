import { useState } from "react";
import { api, dateLabel, useData } from "../../api";
import {
  Badge,
  Button,
  Confirm,
  DataState,
  Empty,
  ErrorBox,
  Field,
  Icon,
  Modal,
  PageHeading,
  RefreshButton,
  Select,
  useApp,
  useMutation,
} from "../../components";

export function AdminAppointments() {
  const state = useData("/admin/appointments", []),
    { changed, notify } = useApp();
  const [edit, setEdit] = useState(null),
    [remove, setRemove] = useState(null),
    [search, setSearch] = useState(""),
    [status, setStatus] = useState("all");
  const shown = (state.data || [])
    .filter(
      (a) =>
        (status === "all" || a.status === status) &&
        `${a.doctorName} ${a.patientName} ${a.date}`
          .toLocaleLowerCase("pl")
          .includes(search.toLocaleLowerCase("pl")),
    )
    .sort((a, b) => `${b.date}${b.time}`.localeCompare(`${a.date}${a.time}`));
  return (
    <>
      <PageHeading
        title="Wszystkie wizyty"
        description="Przegląd rezerwacji i administracyjna korekta danych wizyt."
        actions={<RefreshButton onClick={state.refresh} />}
      />
      <section className="card table-card">
        <div className="table-toolbar">
          <Select
            label="Status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="all">Wszystkie statusy</option>
            <option value="confirmed">Potwierdzone</option>
            <option value="completed">Zakończone</option>
            <option value="cancelled">Anulowane</option>
            <option value="noshow">Nieobecności</option>
          </Select>
          <input
            className="search-input"
            aria-label="Szukaj wizyty"
            placeholder="Pacjent, lekarz lub data…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <DataState state={state} empty="Brak wizyt">
          {() =>
            shown.length ? (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Termin</th>
                      <th>Pacjent</th>
                      <th>Lekarz</th>
                      <th>Status</th>
                      <th className="align-right">Działania</th>
                    </tr>
                  </thead>
                  <tbody>
                    {shown.map((a) => (
                      <tr key={a.id}>
                        <td>
                          <strong>
                            {new Date(`${a.date}T12:00:00`).toLocaleDateString(
                              "pl-PL",
                            )}
                          </strong>
                          <small>
                            {a.time?.slice(0, 5)}
                            {a.endTime ? `–${a.endTime.slice(0, 5)}` : ""}
                          </small>
                        </td>
                        <td>{a.patientName}</td>
                        <td>{a.doctorName}</td>
                        <td>
                          <Badge status={a.status} />
                        </td>
                        <td>
                          <div className="table-actions">
                            <button
                              className="icon-button"
                              aria-label={`Edytuj wizytę ${a.patientName}`}
                              onClick={() => setEdit(a)}
                            >
                              <Icon name="edit" size={17} />
                            </button>
                            <button
                              className="icon-button danger-icon"
                              aria-label={`Usuń wizytę ${a.patientName}`}
                              onClick={() => setRemove(a)}
                            >
                              <Icon name="trash" size={17} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <Empty title="Nie znaleziono wizyt" />
            )
          }
        </DataState>
      </section>
      {edit && (
        <AppointmentEditor appointment={edit} onClose={() => setEdit(null)} />
      )}{" "}
      {remove && (
        <Confirm
          title="Usunąć wizytę?"
          label="Usuń wizytę"
          onClose={() => setRemove(null)}
          onConfirm={async () => {
            await api(`/admin/appointments/${remove.id}`, { method: "DELETE" });
            changed();
            notify("Wizyta została usunięta.");
          }}
        >
          {remove.patientName} · {remove.doctorName} · {dateLabel(remove.date)}{" "}
          o {remove.time?.slice(0, 5)}. Usunięcie jest nieodwracalne.
        </Confirm>
      )}
    </>
  );
}

function AppointmentEditor({ appointment, onClose }) {
  const doctors = useData("/doctors", []),
    users = useData("/admin/users", []),
    mutation = useMutation(),
    { changed, notify } = useApp();
  const [form, setForm] = useState(() => ({
    patientId:
      appointment.patientId == null ? "" : String(appointment.patientId),
    doctorId: appointment.doctorId == null ? "" : String(appointment.doctorId),
    date: appointment.date || "",
    time: appointment.time?.slice(0, 5) || "",
    status: appointment.status || "confirmed",
  }));
  const patients = (users.data || []).filter(
    (user) => user.role === "patient" && user.patientId,
  );
  const patientValid = patients.some(
    (patient) => String(patient.patientId) === form.patientId,
  );
  const doctorValid = (doctors.data || []).some(
    (doctor) => String(doctor.id) === form.doctorId,
  );
  const participantsReady =
    !users.loading && !doctors.loading && !users.error && !doctors.error;
  const update = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
    mutation.setError("");
  };
  async function submit(event) {
    event.preventDefault();
    if (!participantsReady || !patientValid || !doctorValid) {
      mutation.setError(
        "Wybierz pacjenta i lekarza z poprawnie wczytanych list.",
      );
      return;
    }
    const body = {
      ...form,
      doctorId: Number(form.doctorId),
      patientId: Number(form.patientId),
    };
    await mutation.run(async () => {
      await api(`/admin/appointments/${appointment.id}`, {
        method: "PUT",
        body,
      });
      changed();
      notify("Dane wizyty zostały zaktualizowane.");
      onClose();
    });
  }
  return (
    <Modal title="Edytuj wizytę" onClose={onClose}>
      <form onSubmit={submit}>
        <Select
          label="Pacjent"
          name="patientId"
          required
          value={form.patientId}
          onChange={update("patientId")}
          disabled={users.loading || Boolean(users.error)}
        >
          <option value="" disabled>
            Wybierz pacjenta
          </option>
          {form.patientId && !patientValid && (
            <option value={form.patientId} disabled>
              {appointment.patientName} (
              {users.loading ? "wczytywanie…" : "niedostępny"})
            </option>
          )}
          {patients.map((u) => (
            <option key={u.id} value={u.patientId}>
              {u.name}
            </option>
          ))}
        </Select>
        <Select
          label="Lekarz"
          name="doctorId"
          required
          value={form.doctorId}
          onChange={update("doctorId")}
          disabled={doctors.loading || Boolean(doctors.error)}
        >
          <option value="" disabled>
            Wybierz lekarza
          </option>
          {form.doctorId && !doctorValid && (
            <option value={form.doctorId} disabled>
              {appointment.doctorName} (
              {doctors.loading ? "wczytywanie…" : "niedostępny"})
            </option>
          )}
          {doctors.data?.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </Select>
        <div className="form-grid">
          <Field
            label="Data"
            name="date"
            type="date"
            required
            value={form.date}
            onChange={update("date")}
          />
          <Field
            label="Godzina"
            name="time"
            type="time"
            required
            value={form.time}
            onChange={update("time")}
          />
        </div>
        <Select
          label="Status"
          name="status"
          value={form.status}
          onChange={update("status")}
        >
          <option value="confirmed">Potwierdzona</option>
          <option value="completed">Zakończona</option>
          <option value="cancelled">Anulowana</option>
          <option value="noshow">Nieobecność</option>
        </Select>
        <ErrorBox>{mutation.error || doctors.error || users.error}</ErrorBox>
        {participantsReady && (!patientValid || !doctorValid) && (
          <ErrorBox>
            Dotychczasowy pacjent lub lekarz nie jest dostępny na liście.
            Wybierz właściwe osoby przed zapisaniem wizyty.
          </ErrorBox>
        )}
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Wróć
          </Button>
          <Button
            type="submit"
            busy={mutation.busy}
            disabled={!participantsReady || !patientValid || !doctorValid}
          >
            Zapisz zmiany
          </Button>
        </div>
      </form>
    </Modal>
  );
}
