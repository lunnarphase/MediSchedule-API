import { useEffect, useState } from "react";
import { api, stampLabel, useData } from "../../api";
import {
  Badge,
  Button,
  Confirm,
  DataState,
  Empty,
  Modal,
  PageHeading,
  RefreshButton,
  Select,
  useApp,
} from "../../components";

const labels = {
  pending: "Oczekuje",
  processing: "Wysyłanie",
  sent: "Wysłano",
  failed: "Błąd wysyłki",
};
const badges = {
  pending: "confirmed",
  processing: "confirmed",
  sent: "completed",
  failed: "cancelled",
};

export function AdminEmails() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [status, setStatus] = useState("");
  const [query, setQuery] = useState("");
  const [detail, setDetail] = useState(null);
  const [retry, setRetry] = useState(null);
  const { changed, notify } = useApp();
  const path = `/admin/emails?${new URLSearchParams({ page, size, status, q: query })}`;
  const state = useData(path);
  const detailState = useData(detail ? `/admin/emails/${detail.id}` : null);
  useEffect(() => {
    const controller = new AbortController();
    const timer = setInterval(() => {
      api(path, { signal: controller.signal })
        .then(state.replaceData)
        .catch(() => {});
      if (detail)
        api(`/admin/emails/${detail.id}`, { signal: controller.signal })
          .then(detailState.replaceData)
          .catch(() => {});
    }, 5000);
    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [path, detail?.id]);

  return (
    <>
      <PageHeading
        title="E-maile"
        description="Potwierdzenia rezerwacji dla pacjentów i lekarzy."
        actions={<RefreshButton onClick={state.refresh} />}
      />
      <section className="card table-card">
        <div className="table-toolbar">
          <Select
            label="Status wiadomości"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Wszystkie statusy</option>
            {Object.entries(labels).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </Select>
          <form
            className="email-search"
            onSubmit={(event) => {
              event.preventDefault();
              setQuery(new FormData(event.currentTarget).get("q").trim());
              setPage(0);
            }}
          >
            <input
              className="search-input"
              name="q"
              aria-label="Szukaj wiadomości"
              placeholder="Odbiorca lub temat"
              maxLength={255}
            />
            <Button type="submit" variant="secondary">
              Szukaj
            </Button>
          </form>
        </div>
        <DataState state={state}>
          {(data) => (
            <>
              {data.content.length ? (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Odbiorca i temat</th>
                        <th>Utworzono</th>
                        <th>Status</th>
                        <th>Próby</th>
                        <th>Działania</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.content.map((item) => (
                        <tr key={item.id}>
                          <td>
                            <strong>{item.recipient}</strong>
                            <small>{item.subject}</small>
                          </td>
                          <td>{stampLabel(item.createdAt)}</td>
                          <td>
                            <Badge status={badges[item.status]}>
                              {labels[item.status]}
                            </Badge>
                          </td>
                          <td>{item.attempts}</td>
                          <td>
                            <div className="table-actions">
                              <Button
                                variant="secondary"
                                onClick={() => setDetail(item)}
                              >
                                Szczegóły
                              </Button>
                              {item.status === "failed" && (
                                <Button
                                  variant="secondary"
                                  onClick={() => setRetry(item)}
                                >
                                  Ponów
                                </Button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <Empty title="Brak wiadomości dla wybranego filtra" />
              )}
              <nav className="pagination" aria-label="Strony wiadomości">
                <Select
                  label="Na stronie"
                  value={size}
                  onChange={(event) => {
                    setSize(Number(event.target.value));
                    setPage(0);
                  }}
                >
                  {[5, 10, 25, 50].map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </Select>
                <span aria-live="polite">
                  Strona {data.totalPages ? data.number + 1 : 0} z{" "}
                  {data.totalPages} · Łącznie: {data.totalElements}
                </span>
                <div className="pagination-buttons">
                  <Button
                    variant="secondary"
                    disabled={state.loading || page === 0}
                    onClick={() => setPage((value) => value - 1)}
                  >
                    Poprzednia
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={state.loading || page + 1 >= data.totalPages}
                    onClick={() => setPage((value) => value + 1)}
                  >
                    Następna
                  </Button>
                </div>
              </nav>
            </>
          )}
        </DataState>
      </section>
      {detail && (
        <Modal title="Szczegóły wiadomości" onClose={() => setDetail(null)}>
          <DataState state={detailState}>
            {(details) => (
              <>
                <div className="detail-grid">
                  <div>
                    <span>Odbiorca</span>
                    <strong>{details.recipient}</strong>
                  </div>
                  <div>
                    <span>Status</span>
                    <strong>{labels[details.status]}</strong>
                  </div>
                  <div>
                    <span>Wysłano</span>
                    <strong>{stampLabel(details.sentAt)}</strong>
                  </div>
                  <div>
                    <span>Liczba prób</span>
                    <strong>{details.attempts}</strong>
                  </div>
                </div>
                {details.lastError && (
                  <div className="notice error">
                    {details.lastError}
                    {details.status === "pending" && (
                      <p>Kolejna próba: {stampLabel(details.nextAttemptAt)}</p>
                    )}
                  </div>
                )}
                <h3>{details.subject}</h3>
                <p className="email-body">{details.body}</p>
              </>
            )}
          </DataState>
        </Modal>
      )}
      {retry && (
        <Confirm
          title="Ponowić wysyłkę?"
          label="Ponów wysyłkę"
          danger={false}
          onClose={() => setRetry(null)}
          onConfirm={async () => {
            await api(`/admin/emails/${retry.id}/retry`, { method: "POST" });
            changed();
            notify("Wiadomość została dodana do ponownej wysyłki.");
          }}
        >
          Potwierdzenie zostanie ponownie wysłane na adres {retry.recipient}.
        </Confirm>
      )}
    </>
  );
}
