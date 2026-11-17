import { useState } from "react";
import { api, stampLabel, useData } from "../../api";
import {
  Badge,
  Button,
  Confirm,
  DataState,
  Empty,
  Icon,
  Loading,
  Modal,
  PageHeading,
  RefreshButton,
  useApp,
} from "../../components";

export function AdminSecurity() {
  const state = useData("/admin/security"),
    { changed, notify } = useApp();
  const [remove, setRemove] = useState(null),
    [filters, setFilters] = useState({}),
    [showDetails, setShowDetails] = useState(false);
  const attempts =
    state.data?.attempts?.filter(
      (a) =>
        (!filters.email ||
          a.email?.toLowerCase().includes(filters.email.toLowerCase())) &&
        (!filters.ip || a.ipAddress?.includes(filters.ip)),
    ) || [];
  const detail = useData(
    showDetails
      ? `/admin/security/attacks?${new URLSearchParams({ email: filters.email || "", ip: filters.ip || "" })}`
      : null,
  );
  function search(event) {
    event.preventDefault();
    setFilters(Object.fromEntries(new FormData(event.currentTarget)));
    setShowDetails(false);
  }
  return (
    <>
      <PageHeading
        title="Bezpieczeństwo"
        description="Historia logowań, podejrzane próby i blokady adresów IP."
        actions={<RefreshButton onClick={state.refresh} />}
      />
      <DataState state={state}>
        {(data) => (
          <>
            <section className="card section-copy">
              <div className="section-heading">
                <h2>Blokady adresów IP</h2>
                <span className="count">{data?.blockedIps?.length || 0}</span>
              </div>
              {data?.blockedIps?.length ? (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Adres IP</th>
                        <th>Blokada do</th>
                        <th>Powód</th>
                        <th>Działania</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.blockedIps.map((b) => (
                        <tr key={b.ipAddress}>
                          <td>
                            <code>{b.ipAddress}</code>
                          </td>
                          <td>{stampLabel(b.blockedUntil)}</td>
                          <td>{b.reason}</td>
                          <td>
                            <Button
                              variant="secondary"
                              onClick={() => setRemove(b)}
                            >
                              Odblokuj
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="notice success">
                  <Icon name="shield" size={18} /> Brak zablokowanych adresów
                  IP.
                </div>
              )}
            </section>
            <section className="card table-card">
              <div className="table-toolbar">
                <h2>Historia prób logowania</h2>
                <form className="security-search" onSubmit={search}>
                  <input
                    aria-label="Filtruj po e-mailu"
                    name="email"
                    type="text"
                    placeholder="Adres e-mail"
                  />
                  <input
                    aria-label="Filtruj po adresie IP"
                    name="ip"
                    type="text"
                    placeholder="Adres IP"
                  />
                  <Button type="submit" variant="secondary">
                    Filtruj
                  </Button>
                </form>
              </div>
              {attempts.length ? (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Użytkownik</th>
                        <th>Adres IP</th>
                        <th>Data</th>
                        <th>Wynik</th>
                      </tr>
                    </thead>
                    <tbody>
                      {attempts.map((a) => (
                        <tr key={a.id}>
                          <td>{a.email}</td>
                          <td>
                            <code>{a.ipAddress}</code>
                          </td>
                          <td>{stampLabel(a.attemptedAt)}</td>
                          <td>
                            <Badge
                              status={a.success ? "completed" : "cancelled"}
                            >
                              {a.success ? "Zalogowano" : "Nieudana próba"}
                            </Badge>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <Empty title="Brak prób logowania dla wybranego filtra" />
              )}
              {(filters.email || filters.ip) && (
                <div className="table-footer">
                  <Button
                    variant="secondary"
                    onClick={() => setShowDetails(true)}
                  >
                    Zobacz szczegóły aktywności
                  </Button>
                </div>
              )}
            </section>
          </>
        )}
      </DataState>
      {remove && (
        <Confirm
          title="Odblokować adres IP?"
          label="Odblokuj adres"
          danger={false}
          onClose={() => setRemove(null)}
          onConfirm={async () => {
            await api(
              `/admin/security/blocks/${encodeURIComponent(remove.ipAddress)}`,
              { method: "DELETE" },
            );
            changed();
            notify("Blokada adresu IP została usunięta.");
          }}
        >
          {remove.ipAddress}. Użytkownicy tego adresu będą mogli ponownie
          próbować logowania.
        </Confirm>
      )}
      {showDetails && (
        <Modal
          title="Szczegóły aktywności"
          onClose={() => setShowDetails(false)}
          wide
        >
          <DataState state={detail}>
            {(data) => <SecurityDetails data={data} />}
          </DataState>
        </Modal>
      )}
    </>
  );
}

function SecurityDetails({ data }) {
  if (!data) return <Loading />;
  return (
    <>
      <div className="detail-grid">
        <div>
          <span>Nieudane próby</span>
          <strong>{data.totalFailures}</strong>
        </div>
        <div>
          <span>Różne adresy IP</span>
          <strong>{data.distinctIps}</strong>
        </div>
      </div>
      {data.attempts.length ? (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>E-mail</th>
                <th>IP</th>
                <th>Data</th>
                <th>Wynik</th>
              </tr>
            </thead>
            <tbody>
              {data.attempts.map((attempt) => (
                <tr key={attempt.id}>
                  <td>{attempt.email}</td>
                  <td>{attempt.ipAddress}</td>
                  <td>{stampLabel(attempt.attemptedAt)}</td>
                  <td>{attempt.success ? "Sukces" : "Niepowodzenie"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <Empty title="Brak prób logowania dla wybranego filtra" />
      )}
    </>
  );
}
