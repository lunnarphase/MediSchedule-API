import { useState } from "react";
import { api } from "../../api";
import {
  Button,
  Confirm,
  ErrorBox,
  Field,
  Icon,
  Loading,
  Modal,
  Select,
  useApp,
  useMutation,
} from "../../components";

export function ScheduleTemplates({ state, selected, onSelect, busy }) {
  const [creating, setCreating] = useState(false),
    [removing, setRemoving] = useState(null);
  const { changed } = useApp();
  return (
    <aside>
      <section className="card">
        <div className="section-heading">
          <h2>Szablony godzin</h2>
          <button
            className="icon-button"
            aria-label="Dodaj szablon"
            disabled={busy}
            onClick={() => setCreating(true)}
          >
            <span className="plain-plus">+</span>
          </button>
        </div>
        <ErrorBox retry={state.refresh}>{state.error}</ErrorBox>
        {state.loading && !state.data?.length ? (
          <Loading />
        ) : (
          <>
            <Select
              label="Aktywny szablon"
              value={selected}
              disabled={busy || state.loading}
              onChange={(event) => onSelect(event.target.value)}
            >
              <option value="">Wybierz szablon</option>
              {(state.data || []).map((template) => (
                <option key={template.id} value={template.id}>
                  {template.name} ({template.startTime.slice(0, 5)}–
                  {template.endTime.slice(0, 5)})
                </option>
              ))}
            </Select>
            <div className="template-list">
              {(state.data || []).map((template) => (
                <div className="template-item" key={template.id}>
                  <div>
                    <strong>{template.name}</strong>
                    <span>
                      {template.startTime.slice(0, 5)}–
                      {template.endTime.slice(0, 5)}
                    </span>
                  </div>
                  <button
                    className="icon-button danger-icon"
                    aria-label={`Usuń szablon ${template.name}`}
                    disabled={busy}
                    onClick={() => setRemoving(template)}
                  >
                    <Icon name="trash" size={16} />
                  </button>
                </div>
              ))}
            </div>
          </>
        )}
        <Button
          variant="secondary"
          className="full-width"
          disabled={busy}
          onClick={() => setCreating(true)}
        >
          Utwórz szablon
        </Button>
      </section>
      {creating && <TemplateModal onClose={() => setCreating(false)} />}
      {removing && (
        <Confirm
          title="Usunąć szablon?"
          label="Usuń szablon"
          onClose={() => setRemoving(null)}
          onConfirm={async () => {
            await api(`/doctor/templates/${removing.id}`, { method: "DELETE" });
            if (String(removing.id) === selected) onSelect("");
            changed();
          }}
        >
          {removing.name}. Usunięcie szablonu nie zmieni zapisanego grafiku.
        </Confirm>
      )}
    </aside>
  );
}

function TemplateModal({ onClose }) {
  const mutation = useMutation(),
    { changed, notify } = useApp();
  async function submit(event) {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    if (body.startTime >= body.endTime) {
      mutation.setError("Początek musi być wcześniejszy od końca przedziału.");
      return;
    }
    await mutation.run(async () => {
      await api("/doctor/templates", { method: "POST", body });
      changed();
      notify("Szablon został zapisany.");
      onClose();
    });
  }
  return (
    <Modal title="Nowy szablon godzin" onClose={onClose}>
      <form onSubmit={submit}>
        <Field
          label="Nazwa szablonu"
          name="name"
          required
          maxLength={100}
          placeholder="Np. Dyżur poranny"
        />
        <div className="form-grid">
          <Field
            label="Od godziny"
            name="startTime"
            type="time"
            required
            defaultValue="09:00"
          />
          <Field
            label="Do godziny"
            name="endTime"
            type="time"
            required
            defaultValue="17:00"
          />
        </div>
        <ErrorBox>{mutation.error}</ErrorBox>
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Wróć
          </Button>
          <Button busy={mutation.busy} type="submit">
            Zapisz szablon
          </Button>
        </div>
      </form>
    </Modal>
  );
}
