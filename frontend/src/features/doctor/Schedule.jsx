import { useState } from "react";
import { addDays, api, dateLabel, localDate, useData } from "../../api";
import {
  Badge,
  Button,
  Confirm,
  ErrorBox,
  Icon,
  Loading,
  PageHeading,
  useApp,
  useMutation,
} from "../../components";

function monday(value) {
  const date = new Date(`${value}T12:00:00`);
  return addDays(value, -((date.getDay() + 6) % 7));
}

function editorRanges(data) {
  return data.map((range) => ({
    ...range,
    key: String(range.id),
    startTime: range.startTime.slice(0, 5),
    endTime: range.endTime.slice(0, 5),
  }));
}

export function Schedule() {
  const [week, setWeek] = useState(() => monday(localDate()));
  const [draft, setDraft] = useState(null),
    [pendingWeek, setPendingWeek] = useState(null);
  const end = addDays(week, 6);
  const state = useData(`/doctor/availability?from=${week}&to=${end}`, []);
  const mutation = useMutation();
  const { changed, notify } = useApp();
  const dirty = draft !== null;
  const ranges = draft ?? editorRanges(state.data || []);

  function update(change) {
    setDraft((current) => change(current ?? editorRanges(state.data || [])));
    mutation.setError("");
  }
  function chooseWeek(next) {
    if (next === week) return;
    if (dirty) setPendingWeek(next);
    else setWeek(next);
  }
  function addRange(date) {
    update((current) => [
      ...current,
      { key: crypto.randomUUID(), date, startTime: "09:00", endTime: "17:00" },
    ]);
  }

  async function save() {
    if (
      ranges.some(
        (range) =>
          !range.startTime ||
          !range.endTime ||
          range.startTime >= range.endTime,
      )
    ) {
      mutation.setError(
        "Godzina rozpoczęcia każdego przedziału musi być wcześniejsza od zakończenia.",
      );
      return;
    }
    const from = week < localDate() ? localDate() : week;
    await mutation.run(async () => {
      const saved = await api("/doctor/availability", {
        method: "PUT",
        body: {
          from,
          to: end,
          ranges: ranges
            .filter((range) => range.date >= from)
            .map(({ date, startTime, endTime }) => ({
              date,
              startTime,
              endTime,
            })),
        },
      });
      state.replaceData(saved);
      setDraft(null);
      changed();
      notify("Grafik został zapisany.");
    });
  }

  return (
    <>
      <PageHeading
        title="Mój grafik"
        description="Godziny przyjęć i dostępne terminy wizyt."
        actions={
          <Button
            busy={mutation.busy}
            disabled={!dirty || state.loading || !!state.error}
            onClick={save}
          >
            <Icon name="check" size={17} />
            Zapisz grafik
          </Button>
        }
      />
      <ErrorBox retry={state.error ? state.refresh : undefined}>
        {mutation.error || state.error}
      </ErrorBox>
      <div className="">
        <section className="card schedule-card">
          <div className="week-navigation">
            <button
              className="icon-button"
              aria-label="Poprzedni tydzień"
              disabled={mutation.busy || state.loading}
              onClick={() => chooseWeek(addDays(week, -7))}
            >
              <span className="reverse-chevron">
                <Icon name="chevron" />
              </span>
            </button>
            <div>
              <strong>
                {new Date(`${week}T12:00:00`).toLocaleDateString("pl-PL", {
                  day: "numeric",
                  month: "long",
                })}{" "}
                –{" "}
                {new Date(`${end}T12:00:00`).toLocaleDateString("pl-PL", {
                  day: "numeric",
                  month: "long",
                  year: "numeric",
                })}
              </strong>
              <button
                className="text-button small-copy"
                disabled={mutation.busy || state.loading}
                onClick={() => chooseWeek(monday(localDate()))}
              >
                Bieżący tydzień
              </button>
            </div>
            <button
              className="icon-button"
              aria-label="Następny tydzień"
              disabled={mutation.busy || state.loading}
              onClick={() => chooseWeek(addDays(week, 7))}
            >
              <Icon name="chevron" />
            </button>
          </div>
          {dirty && (
            <div className="notice warning">
              Masz niezapisane zmiany w grafiku.
            </div>
          )}
          {state.loading ? (
            <Loading />
          ) : state.error ? null : (
            <div className="schedule-days">
              {Array.from({ length: 7 }, (_, index) =>
                addDays(week, index),
              ).map((date) => {
                const past = date < localDate();
                const dayRanges = ranges.filter((range) => range.date === date);
                return (
                  <article
                    className={`schedule-day ${past ? "past" : ""}`}
                    key={date}
                  >
                    <div className="schedule-date">
                      <strong>
                        {new Date(`${date}T12:00:00`).toLocaleDateString(
                          "pl-PL",
                          { weekday: "long" },
                        )}
                      </strong>
                      <span>
                        {new Date(`${date}T12:00:00`).toLocaleDateString(
                          "pl-PL",
                          { day: "numeric", month: "short" },
                        )}
                      </span>
                      {date === localDate() && (
                        <Badge status="confirmed">Dzisiaj</Badge>
                      )}
                    </div>
                    <div className="schedule-ranges">
                      {dayRanges.length ? (
                        dayRanges.map((range) => (
                          <div className="schedule-range" key={range.key}>
                            <label>
                              <span className="sr-only">
                                Rozpoczęcie, {dateLabel(date)}
                              </span>
                              <input
                                type="time"
                                value={range.startTime}
                                disabled={past || mutation.busy}
                                onChange={(event) =>
                                  update((items) =>
                                    items.map((item) =>
                                      item.key === range.key
                                        ? {
                                            ...item,
                                            startTime: event.target.value,
                                          }
                                        : item,
                                    ),
                                  )
                                }
                              />
                            </label>
                            <span>–</span>
                            <label>
                              <span className="sr-only">
                                Zakończenie, {dateLabel(date)}
                              </span>
                              <input
                                type="time"
                                value={range.endTime}
                                disabled={past || mutation.busy}
                                onChange={(event) =>
                                  update((items) =>
                                    items.map((item) =>
                                      item.key === range.key
                                        ? {
                                            ...item,
                                            endTime: event.target.value,
                                          }
                                        : item,
                                    ),
                                  )
                                }
                              />
                            </label>
                            {!past && (
                              <button
                                className="icon-button danger-icon"
                                aria-label={`Usuń przedział ${range.startTime} w dniu ${date}`}
                                disabled={mutation.busy}
                                onClick={() =>
                                  update((items) =>
                                    items.filter(
                                      (item) => item.key !== range.key,
                                    ),
                                  )
                                }
                              >
                                <Icon name="trash" size={16} />
                              </button>
                            )}
                          </div>
                        ))
                      ) : (
                        <span className="muted small-copy">
                          Brak godzin przyjęć
                        </span>
                      )}
                    </div>
                    {!past && (
                      <div className="schedule-day-actions">
                        <button
                          className="text-button"
                          disabled={mutation.busy}
                          onClick={() => addRange(date)}
                        >
                          + Dodaj godziny
                        </button>
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          )}
          <div className="schedule-save">
            <p className="muted small-copy">
              Grafik musi obejmować już zarezerwowane wizyty.
            </p>
            <Button
              busy={mutation.busy}
              disabled={!dirty || state.loading || !!state.error}
              onClick={save}
            >
              Zapisz zmiany
            </Button>
          </div>
        </section>
      </div>
      {pendingWeek && (
        <Confirm
          title="Pominąć niezapisane zmiany?"
          label="Przejdź do tygodnia"
          onClose={() => setPendingWeek(null)}
          onConfirm={async () => {
            setDraft(null);
            setWeek(pendingWeek);
            mutation.setError("");
          }}
        >
          Zmiany w bieżącym tygodniu nie zostały zapisane.
        </Confirm>
      )}
    </>
  );
}
