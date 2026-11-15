import {
  createContext,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";

export const AppContext = createContext(null);
export const useApp = () => useContext(AppContext);
export function Icon({ name = "plus", size = 20, ...props }) {
  const paths = {
    plus: (
      <>
        <path d="M12 8v8m-4-4h8" />
        <rect x="3" y="6" width="18" height="15" rx="3" />
        <path d="M9 6V3h6v3" />
      </>
    ),
    calendar: (
      <>
        <rect x="3" y="5" width="18" height="16" rx="3" />
        <path d="M7 3v4m10-4v4M3 11h18m-13 4h2m4 0h2" />
      </>
    ),
    users: (
      <>
        <circle cx="9" cy="8" r="4" />
        <path d="M2 21v-3a7 7 0 0 1 14 0v3m0-17a4 4 0 0 1 0 8m3 4a6 6 0 0 1 3 5" />
      </>
    ),
    user: (
      <>
        <circle cx="12" cy="7" r="4" />
        <path d="M4 21v-2a8 8 0 0 1 16 0v2z" />
      </>
    ),
    bell: (
      <>
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9m-12 9h12m-8 3h4" />
      </>
    ),
    search: (
      <>
        <circle cx="10" cy="10" r="7" />
        <path d="m15 15 6 6" />
      </>
    ),
    logout: (
      <>
        <path d="M10 4H4v16h6m4-12 4 4-4 4m-6-4h13" />
      </>
    ),
    chevron: <path d="m9 5 7 7-7 7" />,
    close: <path d="m6 6 12 12M6 18 18 6" />,
    clock: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v6l4 2" />
      </>
    ),
    star: (
      <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z" />
    ),
    check: <path d="m5 12 4 4L19 6" />,
    refresh: (
      <>
        <path d="M20 8a9 9 0 1 0 1 8M20 3v6h-6" />
      </>
    ),
    chart: (
      <>
        <path d="M3 3v18h19M7 16v-4m5 4V8m5 8V5" />
      </>
    ),
    menu: <path d="M3 5h18M3 12h18M3 19h18" />,
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      {paths[name] || paths.plus}
    </svg>
  );
}
export function Brand({ small = false }) {
  return (
    <div className={`brand ${small ? "small" : ""}`}>
      <span className="brand-mark">
        <Icon name="plus" size={small ? 22 : 29} />
      </span>
      <span>MediSchedule</span>
    </div>
  );
}
export function Button({
  children,
  variant = "primary",
  className = "",
  busy = false,
  disabled = false,
  ...props
}) {
  return (
    <button
      className={`btn btn-${variant} ${className}`}
      disabled={busy || disabled}
      {...props}
    >
      {busy ? <span className="spinner small" /> : null}
      {children}
    </button>
  );
}
export function Field({ label, children, hint, className = "", ...props }) {
  const id = useId();
  return (
    <div className={`field ${className}`}>
      <label htmlFor={id}>{label}</label>
      {children ? children(id) : <input id={id} {...props} />}{" "}
      {hint && <small>{hint}</small>}
    </div>
  );
}
export function Select({ label, children, ...props }) {
  return (
    <Field label={label}>
      {(id) => (
        <select id={id} {...props}>
          {children}
        </select>
      )}
    </Field>
  );
}
export function Textarea({ label, ...props }) {
  return <Field label={label}>{(id) => <textarea id={id} {...props} />}</Field>;
}
export function ErrorBox({ children, retry }) {
  return children ? (
    <div className="notice error" role="alert">
      <span>{children}</span>
      {retry && (
        <button className="text-button" onClick={retry}>
          Spróbuj ponownie
        </button>
      )}
    </div>
  ) : null;
}
export function SuccessBox({ children }) {
  return children ? (
    <div className="notice success" role="status">
      {children}
    </div>
  ) : null;
}
export function Loading({ label = "Wczytywanie danych…" }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" />
      {label}
    </div>
  );
}
export function Empty({ title = "Brak danych", children, icon = "calendar" }) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <Icon name={icon} size={30} />
      </span>
      <h3>{title}</h3>
      {children && <p>{children}</p>}
    </div>
  );
}
export function DataState({ state, children, empty = "Brak danych" }) {
  if (
    state.loading &&
    (!state.data || (Array.isArray(state.data) && !state.data.length))
  )
    return <Loading />;
  if (state.error)
    return <ErrorBox retry={state.refresh}>{state.error}</ErrorBox>;
  if (Array.isArray(state.data) && !state.data.length)
    return <Empty title={empty} />;
  return children(state.data);
}
const modalStack = [];
let modalBodyOverflow = "";

export function Modal({ title, children, onClose, wide = false }) {
  const titleId = useId();
  const ref = useRef(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    const previous = document.activeElement;
    const panel = ref.current;
    if (!modalStack.length) modalBodyOverflow = document.body.style.overflow;
    modalStack.push(panel);
    document.body.style.overflow = "hidden";
    const focusables = () =>
      [
        ...(ref.current?.querySelectorAll(
          'button:not([disabled]), input, select, textarea, a[href], [tabindex="0"]',
        ) || []),
      ].filter((el) => el.offsetParent !== null);
    focusables()[0]?.focus();
    const key = (e) => {
      if (modalStack.at(-1) !== panel) return;
      if (e.key === "Escape") closeRef.current();
      if (e.key === "Tab") {
        const list = focusables(),
          first = list[0],
          last = list.at(-1);
        if (!list.length) return;
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", key);
    return () => {
      const index = modalStack.indexOf(panel);
      if (index >= 0) modalStack.splice(index, 1);
      if (!modalStack.length) document.body.style.overflow = modalBodyOverflow;
      document.removeEventListener("keydown", key);
      if (previous?.isConnected) previous.focus();
    };
  }, []);
  return createPortal(
    <div
      className="modal-overlay"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <section
        ref={ref}
        className={`modal ${wide ? "wide" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <header className="modal-header">
          <h2 id={titleId}>{title}</h2>
          <button
            className="icon-button"
            aria-label="Zamknij okno"
            onClick={onClose}
          >
            <Icon name="close" />
          </button>
        </header>
        <div className="modal-body">{children}</div>
      </section>
    </div>,
    document.body,
  );
}
export function Confirm({
  title,
  children,
  onClose,
  onConfirm,
  label = "Potwierdź",
  danger = true,
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function confirm() {
    setBusy(true);
    setError("");
    try {
      await onConfirm();
      onClose();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title={title} onClose={() => !busy && onClose()}>
      <p className="confirm-copy">{children}</p>
      <ErrorBox>{error}</ErrorBox>
      <div className="form-actions">
        <Button variant="secondary" onClick={onClose} disabled={busy}>
          Wróć
        </Button>
        <Button
          variant={danger ? "danger" : "primary"}
          onClick={confirm}
          busy={busy}
        >
          {label}
        </Button>
      </div>
    </Modal>
  );
}
export const statusNames = {
  confirmed: "Potwierdzona",
  completed: "Zakończona",
  cancelled: "Anulowana",
  noshow: "Nieobecność",
};
export function Badge({ status, children }) {
  return (
    <span className={`badge ${String(status).toLowerCase()}`}>
      {children || statusNames[String(status).toLowerCase()] || status}
    </span>
  );
}
const polishPluralRules = new Intl.PluralRules("pl");
function reviewCountLabel(count) {
  const forms = {
    one: "opinia",
    few: "opinie",
    many: "opinii",
    other: "opinii",
  };
  return `${count} ${forms[polishPluralRules.select(Number(count))]}`;
}
export function Stars({ value = 0, count }) {
  return (
    <span className="rating">
      <Icon name="star" size={16} />
      <strong>{Number(value || 0).toFixed(1)}</strong>
      {count !== undefined && <span>({reviewCountLabel(count)})</span>}
    </span>
  );
}
export function PageHeading({ eyebrow, title, description, actions }) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {actions && <div className="heading-actions">{actions}</div>}
    </div>
  );
}
export function RefreshButton({ onClick }) {
  return (
    <Button variant="secondary" onClick={onClick}>
      <Icon name="refresh" size={16} />
      Odśwież
    </Button>
  );
}
export function Stat({ icon, title, value, tone = "" }) {
  return (
    <div className={`stat ${tone}`}>
      <span className="stat-icon">
        <Icon name={icon} />
      </span>
      <div>
        <span>{title}</span>
        <strong>{value}</strong>
      </div>
    </div>
  );
}
export function useMutation() {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const run = async (fn) => {
    setBusy(true);
    setError("");
    try {
      return await fn();
    } catch (e) {
      setError(e.message);
      return false;
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, run, setError };
}
