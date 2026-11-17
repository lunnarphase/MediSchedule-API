import { useState } from "react";
import { api, stampLabel, useData } from "../../api";
import {
  Badge,
  Button,
  Confirm,
  DataState,
  Empty,
  ErrorBox,
  Icon,
  Modal,
  PageHeading,
  RefreshButton,
  Stars,
  Textarea,
  useApp,
  useMutation,
} from "../../components";

export function AdminReviews() {
  const state = useData("/admin/reviews", []),
    { changed, notify } = useApp();
  const [search, setSearch] = useState(""),
    [selected, setSelected] = useState(null);
  const shown =
    state.data?.filter((r) =>
      `${r.doctorName} ${r.patientName} ${r.comment}`
        .toLocaleLowerCase("pl")
        .includes(search.toLocaleLowerCase("pl")),
    ) || [];
  return (
    <>
      <PageHeading
        title="Moderacja opinii"
        description="Przeglądaj opinie pacjentów i reaguj na naruszenia."
        actions={<RefreshButton onClick={state.refresh} />}
      />
      <section className="card">
        <div className="section-heading">
          <h2>
            Wszystkie opinie{" "}
            <span className="count">{state.data?.length || 0}</span>
          </h2>
          <input
            className="search-input"
            aria-label="Szukaj opinii"
            placeholder="Szukaj lekarza lub treści…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <DataState state={state} empty="Brak opinii do moderacji">
          {() =>
            shown.length ? (
              <div className="admin-reviews-grid">
                {shown.map((r) => (
                  <article className="review-card" key={r.id}>
                    <div className="review-heading">
                      <div>
                        <strong>{r.patientName}</strong>
                        <time>{stampLabel(r.createdAt)}</time>
                      </div>
                      <Stars value={r.rating} />
                    </div>
                    <p className="small-copy muted">Lekarz: {r.doctorName}</p>
                    <p className="review-comment">
                      {r.comment || "Opinia bez komentarza."}
                    </p>
                    <Button
                      variant="ghost-danger"
                      onClick={() => setSelected(r)}
                    >
                      <Icon name="trash" size={15} />
                      Usuń opinię
                    </Button>
                  </article>
                ))}
              </div>
            ) : (
              <Empty title="Nie znaleziono opinii" />
            )
          }
        </DataState>
      </section>
      {selected && (
        <Confirm
          title="Usunąć opinię?"
          label="Usuń opinię"
          onClose={() => setSelected(null)}
          onConfirm={async () => {
            await api(`/admin/reviews/${selected.id}`, { method: "DELETE" });
            changed();
            notify("Opinia została usunięta.");
          }}
        >
          Opinia pacjenta {selected.patientName} o lekarzu {selected.doctorName}{" "}
          zostanie usunięta.
        </Confirm>
      )}
    </>
  );
}
export function AdminReports() {
  const state = useData("/admin/reports", []);
  const [status, setStatus] = useState("pending"),
    [selected, setSelected] = useState(null);
  const shown =
    state.data?.filter((r) => status === "all" || r.status === status) || [];
  return (
    <>
      <PageHeading
        title="Zgłoszenia opinii"
        description="Rozpatruj zgłoszenia i informuj użytkowników o decyzji."
        actions={<RefreshButton onClick={state.refresh} />}
      />
      <div className="filter-chips section-copy">
        {[
          ["pending", "Oczekujące"],
          ["resolved", "Rozwiązane"],
          ["dismissed", "Odrzucone"],
          ["all", "Wszystkie"],
        ].map(([key, label]) => (
          <button
            key={key}
            className={status === key ? "selected" : ""}
            onClick={() => setStatus(key)}
          >
            {label}
          </button>
        ))}
      </div>
      <DataState state={state} empty="Brak zgłoszeń">
        {() =>
          shown.length ? (
            <div className="reports-grid">
              {shown.map((r) => (
                <article className="card report-card" key={r.id}>
                  <div className="section-heading">
                    <Badge status={r.status} />
                    <time className="small-copy muted">
                      {stampLabel(r.createdAt)}
                    </time>
                  </div>
                  <h3>{categoryLabel(r.category)}</h3>
                  <p className="muted small-copy">
                    Zgłaszający: {r.reporterName}
                  </p>
                  <p className="report-reason">{r.reason}</p>
                  {r.review && (
                    <div>
                      <p className="small-copy muted">
                        Zgłoszona opinia · {r.review.doctorName}
                      </p>
                      {r.review.deleted && (
                        <Badge status="cancelled">
                          Opinia usunięta z widoku publicznego
                        </Badge>
                      )}
                      <blockquote>
                        <Stars value={r.review.rating} />
                        <p>{r.review.comment || "Opinia bez komentarza."}</p>
                      </blockquote>
                    </div>
                  )}
                  {r.adminResponse && (
                    <div className="recommendations">
                      <strong>Odpowiedź administratora</strong>
                      <p>{r.adminResponse}</p>
                    </div>
                  )}
                  {r.status === "pending" && (
                    <div className="form-actions">
                      <Button
                        variant="secondary"
                        onClick={() => setSelected({ ...r, action: "dismiss" })}
                      >
                        Odrzuć zgłoszenie
                      </Button>
                      <Button
                        variant="danger"
                        onClick={() => setSelected({ ...r, action: "resolve" })}
                      >
                        Usuń opinię i rozwiąż
                      </Button>
                    </div>
                  )}
                </article>
              ))}
            </div>
          ) : (
            <section className="card">
              <Empty title="Brak zgłoszeń dla tego statusu">
                Wszystkie zgłoszenia z tej kategorii zostały obsłużone.
              </Empty>
            </section>
          )
        }
      </DataState>
      {selected && (
        <ReportDecision report={selected} onClose={() => setSelected(null)} />
      )}
    </>
  );
}
function categoryLabel(value) {
  return (
    {
      inappropriate: "Niestosowna treść",
      misleading: "Wprowadzanie w błąd",
      privacy: "Naruszenie prywatności",
      spam: "Spam",
      other: "Inny powód",
    }[value] || value
  );
}
function ReportDecision({ report, onClose }) {
  const mutation = useMutation(),
    { changed, notify } = useApp();
  async function submit(event) {
    event.preventDefault();
    const { response } = Object.fromEntries(new FormData(event.currentTarget));
    await mutation.run(async () => {
      await api(`/admin/reports/${report.id}`, {
        method: "PATCH",
        body: { action: report.action, response },
      });
      changed();
      notify("Zgłoszenie zostało rozpatrzone.");
      onClose();
    });
  }
  return (
    <Modal
      title={
        report.action === "resolve"
          ? "Usuń opinię i rozwiąż zgłoszenie"
          : "Odrzuć zgłoszenie"
      }
      onClose={onClose}
    >
      <p className="section-copy">
        {report.action === "resolve"
          ? "Opinia zostanie usunięta, a zgłoszenie oznaczone jako rozwiązane."
          : "Opinia pozostanie widoczna, a zgłoszenie zostanie zamknięte."}
      </p>
      <form onSubmit={submit}>
        <Textarea
          label="Uzasadnienie decyzji"
          name="response"
          required
          minLength={3}
          maxLength={2000}
          rows={4}
        />
        <ErrorBox>{mutation.error}</ErrorBox>
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Wróć
          </Button>
          <Button
            variant={report.action === "resolve" ? "danger" : "primary"}
            type="submit"
            busy={mutation.busy}
          >
            Potwierdź decyzję
          </Button>
        </div>
      </form>
    </Modal>
  );
}
