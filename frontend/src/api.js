import { useCallback, useEffect, useState } from "react";

let csrf;
export const resetCsrf = () => {
  csrf = undefined;
};
async function token() {
  if (!csrf) {
    const response = await fetch("/api/auth/csrf", {
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!response.ok)
      throw new Error("Nie udało się połączyć z serwerem. Spróbuj ponownie.");
    csrf = await response.json();
  }
  return csrf;
}
export async function api(path, options = {}) {
  const method = options.method || "GET";
  const headers = { Accept: "application/json", ...options.headers };
  if (!["GET", "HEAD"].includes(method)) {
    const value = await token();
    headers[value.headerName || "X-CSRF-TOKEN"] = value.token;
  }
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  let response;
  try {
    response = await fetch(`/api${path}`, {
      ...options,
      method,
      credentials: "same-origin",
      headers,
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new Error(
      "Brak połączenia z serwerem. Sprawdź połączenie i spróbuj ponownie.",
    );
  }
  const text = await response.text();
  let data;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!response.ok) {
    if (response.status === 401 && !path.startsWith("/auth/"))
      window.dispatchEvent(new Event("session-expired"));
    if (response.status === 403) resetCsrf();
    const error = new Error(
      data?.message ||
        data?.error ||
        {
          401: "Zaloguj się ponownie.",
          403: "Brak uprawnień do tej operacji.",
          409: "Dane zmieniły się. Odśwież widok i spróbuj ponownie.",
        }[response.status] ||
        "Nie udało się wykonać operacji.",
    );
    error.status = response.status;
    throw error;
  }
  return data;
}
export function useData(path, initial = null) {
  const [state, setState] = useState({
    data: initial,
    loading: true,
    error: "",
  });
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision((v) => v + 1), []);
  useEffect(() => {
    window.addEventListener("data-changed", refresh);
    return () => window.removeEventListener("data-changed", refresh);
  }, [refresh]);
  useEffect(() => {
    if (!path) {
      setState({ data: initial, loading: false, error: "" });
      return;
    }
    const controller = new AbortController();
    setState((s) => ({ ...s, loading: true, error: "" }));
    api(path, { signal: controller.signal })
      .then((data) => setState({ data, loading: false, error: "" }))
      .catch((error) => {
        if (error.name !== "AbortError")
          setState((s) => ({ ...s, loading: false, error: error.message }));
      });
    return () => controller.abort();
  }, [path, revision]);
  return { ...state, refresh };
}
export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function addDays(date, count) {
  const d = new Date(`${date}T12:00:00`);
  d.setDate(d.getDate() + count);
  return localDate(d);
}
export const dateLabel = (value) =>
  value
    ? new Date(`${value}T12:00:00`).toLocaleDateString("pl-PL", {
        weekday: "long",
        day: "numeric",
        month: "long",
        year: "numeric",
      })
    : "—";
export const stampLabel = (value) =>
  value
    ? new Date(value).toLocaleString("pl-PL", {
        dateStyle: "short",
        timeStyle: "short",
      })
    : "—";
export const money = (value) =>
  new Intl.NumberFormat("pl-PL", { style: "currency", currency: "PLN" }).format(
    Number(value || 0),
  );

export function validPhone(value) {
  return /^[+0-9 ()-]*$/.test(value || "");
}
