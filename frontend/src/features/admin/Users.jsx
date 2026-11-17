import { useState } from "react";
import { api, useData, validPhone } from "../../api";
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

const roles = { patient: "Pacjent", doctor: "Lekarz", admin: "Administrator" };
export function AdminUsers() {
  const state = useData("/admin/users", []),
    { user, changed, notify } = useApp();
  const [search, setSearch] = useState(""),
    [role, setRole] = useState("all"),
    [edit, setEdit] = useState(null),
    [action, setAction] = useState(null);
  const shown = (state.data || []).filter(
    (u) =>
      (role === "all" || u.role === role) &&
      `${u.name} ${u.email}`
        .toLocaleLowerCase("pl")
        .includes(search.toLocaleLowerCase("pl")),
  );
  const adminCount =
    state.data?.filter((u) => u.role === "admin" && !u.blocked).length || 0;
  return (
    <>
      <PageHeading
        title="Użytkownicy"
        description="Konta pacjentów, lekarzy i administratorów w jednym miejscu."
        actions={
          <>
            <RefreshButton onClick={state.refresh} />
            <Button onClick={() => setEdit({})}>+ Dodaj użytkownika</Button>
          </>
        }
      />
      <section className="card table-card">
        <div className="table-toolbar">
          <div className="filter-chips">
            {[["all", "Wszyscy"], ...Object.entries(roles)].map(
              ([value, label]) => (
                <button
                  className={role === value ? "selected" : ""}
                  key={value}
                  onClick={() => setRole(value)}
                >
                  {label}
                </button>
              ),
            )}
          </div>
          <input
            aria-label="Szukaj użytkownika"
            className="search-input"
            placeholder="Imię, nazwisko lub e-mail…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <DataState state={state} empty="Nie ma jeszcze użytkowników">
          {() =>
            shown.length ? (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Użytkownik</th>
                      <th>Rola</th>
                      <th>Status</th>
                      <th className="align-right">Działania</th>
                    </tr>
                  </thead>
                  <tbody>
                    {shown.map((u) => {
                      const protectedUser =
                        u.id === user.id ||
                        (u.role === "admin" && !u.blocked && adminCount <= 1);
                      return (
                        <tr key={u.id}>
                          <td>
                            <div className="table-person">
                              <span className="avatar">
                                {u.name
                                  ?.split(" ")
                                  .map((w) => w[0])
                                  .slice(0, 2)
                                  .join("")}
                              </span>
                              <div>
                                <strong>
                                  {u.name}
                                  {u.id === user.id && (
                                    <span className="you-label">Ty</span>
                                  )}
                                </strong>
                                <small>{u.email}</small>
                              </div>
                            </div>
                          </td>
                          <td>
                            <span className={`role-label ${u.role}`}>
                              {roles[u.role]}
                            </span>
                          </td>
                          <td>
                            <Badge
                              status={u.blocked ? "cancelled" : "completed"}
                            >
                              {u.blocked ? "Zablokowany" : "Aktywny"}
                            </Badge>
                          </td>
                          <td>
                            <div className="table-actions">
                              <button
                                className="icon-button"
                                title="Edytuj dane użytkownika"
                                aria-label={`Edytuj ${u.name}`}
                                onClick={() => setEdit(u)}
                              >
                                <Icon name="edit" size={17} />
                              </button>
                              <Button
                                variant="secondary"
                                disabled={protectedUser && !u.blocked}
                                onClick={() =>
                                  setAction({ type: "block", user: u })
                                }
                              >
                                {u.blocked ? "Odblokuj" : "Zablokuj"}
                              </Button>
                              <button
                                className="icon-button danger-icon"
                                title={
                                  protectedUser
                                    ? "Nie możesz usunąć siebie ani ostatniego administratora"
                                    : "Usuń użytkownika"
                                }
                                aria-label={`Usuń ${u.name}`}
                                disabled={protectedUser}
                                onClick={() =>
                                  setAction({ type: "delete", user: u })
                                }
                              >
                                <Icon name="trash" size={17} />
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : (
              <Empty title="Brak użytkowników dla wybranego filtra" />
            )
          }
        </DataState>
      </section>
      {edit && (
        <UserModal
          user={edit}
          roleLocked={
            edit.role === "admin" &&
            (edit.id === user.id || (!edit.blocked && adminCount <= 1))
          }
          onClose={() => setEdit(null)}
        />
      )}{" "}
      {action && (
        <Confirm
          title={
            action.type === "delete"
              ? "Usunąć użytkownika?"
              : action.user.blocked
                ? "Odblokować konto?"
                : "Zablokować konto?"
          }
          label={
            action.type === "delete"
              ? "Usuń użytkownika"
              : action.user.blocked
                ? "Odblokuj"
                : "Zablokuj"
          }
          danger={action.type === "delete" || !action.user.blocked}
          onClose={() => setAction(null)}
          onConfirm={async () => {
            await api(
              `/admin/users/${action.user.id}${action.type === "block" ? "/block" : ""}`,
              {
                method: action.type === "delete" ? "DELETE" : "PATCH",
                ...(action.type === "block"
                  ? { body: { blocked: !action.user.blocked } }
                  : {}),
              },
            );
            changed();
            notify(
              action.type === "delete"
                ? "Użytkownik został usunięty."
                : "Status konta został zmieniony.",
            );
          }}
        >
          {action.type === "delete"
            ? `Konto ${action.user.name} i powiązane dane zostaną usunięte. Ta operacja jest nieodwracalna.`
            : `Zmiana dotyczy konta: ${action.user.name} (${action.user.email}).`}
        </Confirm>
      )}
    </>
  );
}
function UserModal({ user, roleLocked, onClose }) {
  const [role, setRole] = useState(user.role || "patient");
  const specs = useData("/specializations", []);
  const mutation = useMutation(),
    { changed, notify } = useApp();
  async function submit(event) {
    event.preventDefault();
    const body = {
      ...Object.fromEntries(new FormData(event.currentTarget)),
      role,
    };
    if (role === "patient" && !validPhone(body.phone)) {
      mutation.setError(
        "Numer telefonu może zawierać cyfry, spacje oraz znaki + ( ) -.",
      );
      return;
    }
    if (body.specializationId)
      body.specializationId = Number(body.specializationId);
    else delete body.specializationId;
    await mutation.run(async () => {
      await api(`/admin/users${user.id ? `/${user.id}` : ""}`, {
        method: user.id ? "PUT" : "POST",
        body,
      });
      changed();
      notify(
        user.id
          ? "Dane użytkownika zostały zapisane."
          : "Użytkownik został utworzony.",
      );
      onClose();
    });
  }
  return (
    <Modal
      title={user.id ? "Edytuj użytkownika" : "Nowy użytkownik"}
      onClose={onClose}
    >
      <form onSubmit={submit}>
        <Field
          label="Imię i nazwisko"
          name="name"
          required
          minLength={3}
          maxLength={100}
          defaultValue={user.name || ""}
        />
        <Field
          label="Adres e-mail"
          name="email"
          type="email"
          required
          maxLength={255}
          defaultValue={user.email || ""}
        />
        <Select
          label="Rola"
          name="role"
          value={role}
          disabled={roleLocked}
          onChange={(e) => setRole(e.target.value)}
        >
          {Object.entries(roles).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        {!user.id && (
          <Field
            label="Hasło początkowe"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            hint="Minimum 8 znaków, maksymalnie 72 bajty. Mała i wielka litera, cyfra oraz znak specjalny."
          />
        )}
        {role === "patient" && (!user.id || !user.pesel) && (
          <Field
            label="PESEL"
            name="pesel"
            inputMode="numeric"
            pattern="[0-9]{11}"
            minLength={11}
            maxLength={11}
            required
          />
        )}
        {role === "patient" && (
          <Field
            label="Telefon"
            name="phone"
            type="tel"
            maxLength={20}
            defaultValue={user.phone || ""}
          />
        )}{" "}
        {!user.id && role === "doctor" && (
          <Select label="Specjalizacja" name="specializationId">
            <option value="">Bez przypisanej specjalizacji</option>
            {specs.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        )}
        <ErrorBox>{mutation.error || specs.error}</ErrorBox>
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Wróć
          </Button>
          <Button type="submit" busy={mutation.busy}>
            {user.id ? "Zapisz zmiany" : "Utwórz konto"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
