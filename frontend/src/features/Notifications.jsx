import { useState } from "react";
import { api, stampLabel, useData } from "../api";
import {
  Button,
  Confirm,
  DataState,
  ErrorBox,
  Icon,
  PageHeading,
  RefreshButton,
  useApp,
  useMutation,
} from "../components";

export function Notifications() {
  const state = useData("/notifications", []),
    { notify, changed } = useApp();
  const mutation = useMutation();
  const [clear, setClear] = useState(false);
  const perform = (fn) =>
    mutation.run(async () => {
      await fn();
      changed();
    });
  return (
    <>
      <PageHeading
        title="Powiadomienia"
        description="Wiadomości o wizytach, opiniach i ważnych zmianach."
        actions={
          <>
            <RefreshButton onClick={state.refresh} />
            <Button
              variant="secondary"
              busy={mutation.busy}
              disabled={!state.data?.some((n) => !n.read)}
              onClick={() =>
                perform(() => api("/notifications/read", { method: "PATCH" }))
              }
            >
              <Icon name="check" size={16} />
              Oznacz jako przeczytane
            </Button>
            <Button
              variant="ghost-danger"
              disabled={!state.data?.length}
              onClick={() => setClear(true)}
            >
              Wyczyść
            </Button>
          </>
        }
      />
      <ErrorBox>{mutation.error}</ErrorBox>
      <section className="card notification-card">
        <DataState state={state} empty="Nie masz jeszcze powiadomień">
          {(data) => (
            <div className="notification-list">
              {data.map((n) => (
                <article
                  key={n.id}
                  className={`notification ${n.read ? "" : "unread"}`}
                >
                  <span className="notification-icon">
                    <Icon name={n.type?.includes("review") ? "star" : "bell"} />
                  </span>
                  <div>
                    <p>{n.message}</p>
                    <time>{stampLabel(n.createdAt)}</time>
                  </div>
                  <button
                    className="icon-button"
                    aria-label="Usuń powiadomienie"
                    disabled={mutation.busy}
                    onClick={() =>
                      perform(() =>
                        api(`/notifications/${n.id}`, { method: "DELETE" }),
                      )
                    }
                  >
                    <Icon name="close" size={17} />
                  </button>
                </article>
              ))}
            </div>
          )}
        </DataState>
      </section>
      {clear && (
        <Confirm
          title="Wyczyścić powiadomienia?"
          label="Wyczyść powiadomienia"
          onClose={() => setClear(false)}
          onConfirm={async () => {
            await api("/notifications", { method: "DELETE" });
            changed();
            notify("Powiadomienia zostały usunięte.");
          }}
        >
          Wszystkie powiadomienia zostaną usunięte z Twojego konta.
        </Confirm>
      )}
    </>
  );
}
