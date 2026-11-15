import { useEffect, useState } from "react";
import { addDays, api, dateLabel, localDate, money, useData } from "../../api";
import {
  Button,
  DataState,
  Empty,
  ErrorBox,
  Field,
  Icon,
  Loading,
  Modal,
  PageHeading,
  RefreshButton,
  Select,
  Stars,
  useApp,
  useMutation,
} from "../../components";
import { ReviewList } from "./Reviews";

export function DoctorCard({ doctor, onSelect, compact = false }) {
  return (
    <article className={`doctor-card ${compact ? "compact" : ""}`}>
      <span className="doctor-avatar">
        <Icon name="user" size={compact ? 23 : 32} />
      </span>
      <div className="doctor-summary">
        <p className="eyebrow">
          {doctor.specializations?.join(" · ") || "Konsultacje medyczne"}
        </p>
        <h3>{doctor.name}</h3>
        <Stars value={doctor.averageRating} count={doctor.reviewCount || 0} />
        {!compact && (
          <p className="muted doctor-bio">
            {doctor.bio || "Umów konsultację i zadbaj o swoje zdrowie."}
          </p>
        )}
      </div>
      <div className="doctor-card-action">
        {!compact && (
          <div className="doctor-price">
            <strong>{money(doctor.visitPrice)}</strong>
            <small>
              <Icon name="clock" size={13} />
              {doctor.visitDuration} min
            </small>
          </div>
        )}
        <Button onClick={() => onSelect(doctor)}>
          {compact ? "Umów" : "Zobacz terminy"}
          <Icon name="chevron" size={15} />
        </Button>
      </div>
    </article>
  );
}

export function DoctorModal({ doctor, onClose }) {
  const { notify, changed } = useApp();
  const detail = useData(`/doctors/${doctor.id}`),
    dates = useData(
      `/doctors/${doctor.id}/available-dates?from=${localDate()}&to=${addDays(localDate(), 60)}`,
      [],
    ),
    reviews = useData(`/doctors/${doctor.id}/reviews`, []);
  const [date, setDate] = useState(""),
    [time, setTime] = useState(""),
    [tab, setTab] = useState("booking");
  const slots = useData(
      date ? `/doctors/${doctor.id}/slots?date=${date}` : null,
      [],
    ),
    mutation = useMutation();
  useEffect(() => {
    if (!date && dates.data?.length) setDate(dates.data[0]);
  }, [dates.data]);
  useEffect(() => {
    if (time && !slots.loading && !slots.error && !slots.data?.includes(time)) {
      setTime("");
    }
  }, [time, slots.data, slots.loading, slots.error]);
  const d = detail.data || doctor;
  async function book(event) {
    event.preventDefault();
    if (!time || !date) {
      mutation.setError("Wybierz datę i godzinę wizyty.");
      return;
    }
    await mutation.run(async () => {
      try {
        await api("/appointments", {
          method: "POST",
          body: { doctorId: doctor.id, date, time },
        });
      } catch (error) {
        if (error.status === 409) {
          setTime("");
          slots.refresh();
          dates.refresh();
          error.message =
            "Wybrany termin jest już zajęty. Odświeżamy dostępność — wybierz inną godzinę lub dzień i potwierdź rezerwację ponownie.";
        }
        throw error;
      }
      notify("Wizyta została zarezerwowana.");
      changed();
      onClose();
    });
  }
  return (
    <Modal title="Umów konsultację" onClose={onClose} wide>
      <div className="doctor-modal-profile">
        <span className="doctor-avatar large">
          <Icon name="user" size={40} />
        </span>
        <div>
          <p className="eyebrow">{d.specializations?.join(" · ")}</p>
          <h2>{d.name}</h2>
          <Stars value={d.averageRating} count={d.reviewCount || 0} />
        </div>
        <div className="doctor-price">
          <strong>{money(d.visitPrice)}</strong>
          <small>{d.visitDuration} min konsultacji</small>
        </div>
      </div>
      {d.bio && <p className="doctor-detail-bio">{d.bio}</p>}
      <div className="tabs" role="tablist" aria-label="Szczegóły lekarza">
        <button
          role="tab"
          aria-selected={tab === "booking"}
          className={tab === "booking" ? "active" : ""}
          onClick={() => setTab("booking")}
        >
          Dostępne terminy
        </button>
        <button
          role="tab"
          aria-selected={tab === "reviews"}
          className={tab === "reviews" ? "active" : ""}
          onClick={() => setTab("reviews")}
        >
          Opinie ({reviews.data?.length || 0})
        </button>
      </div>
      {tab === "booking" ? (
        <form onSubmit={book}>
          <ErrorBox>{detail.error || dates.error}</ErrorBox>
          <div className="booking-top">
            <div>
              <h3>Wybierz dzień wizyty</h3>
              <p className="muted">Wolne terminy na najbliższe 60 dni</p>
            </div>
            <Field
              label="Data wizyty"
              type="date"
              value={date}
              min={localDate()}
              max={addDays(localDate(), 60)}
              required
              onChange={(e) => {
                setDate(e.target.value);
                setTime("");
                mutation.setError("");
              }}
            />
          </div>
          {dates.loading ? (
            <Loading label="Sprawdzamy dostępność…" />
          ) : dates.data?.length ? (
            <div className="date-chips">
              {dates.data.slice(0, 14).map((value) => {
                const day = new Date(`${value}T12:00:00`);
                return (
                  <button
                    type="button"
                    key={value}
                    aria-pressed={date === value}
                    className={`date-chip ${date === value ? "selected" : ""}`}
                    onClick={() => {
                      setDate(value);
                      setTime("");
                      mutation.setError("");
                    }}
                  >
                    <span>
                      {day.toLocaleDateString("pl-PL", { weekday: "short" })}
                    </span>
                    <strong>{day.getDate()}</strong>
                    <span>
                      {day.toLocaleDateString("pl-PL", { month: "short" })}
                    </span>
                  </button>
                );
              })}
            </div>
          ) : (
            <Empty title="Brak dostępnych terminów">
              Lekarz nie opublikował jeszcze wolnych terminów. Sprawdź później
              lub wybierz innego specjalistę.
            </Empty>
          )}
          {date && (
            <div className="slot-section">
              <h3>{dateLabel(date)}</h3>
              {slots.loading ? (
                <Loading label="Wczytywanie godzin…" />
              ) : slots.error ? (
                <ErrorBox retry={slots.refresh}>{slots.error}</ErrorBox>
              ) : slots.data?.length ? (
                <div className="slot-grid">
                  {slots.data.map((slot) => (
                    <button
                      type="button"
                      key={slot}
                      aria-pressed={time === slot}
                      className={`time-slot ${time === slot ? "selected" : ""}`}
                      onClick={() => {
                        setTime(slot);
                        mutation.setError("");
                      }}
                    >
                      {slot.slice(0, 5)}
                    </button>
                  ))}
                </div>
              ) : (
                <p className="notice">
                  W tym dniu nie ma już wolnych godzin. Wybierz inny dzień.
                </p>
              )}
            </div>
          )}
          <ErrorBox>{mutation.error}</ErrorBox>
          <div className="booking-summary">
            <div>
              <small>Wybrana wizyta</small>
              <strong>
                {time
                  ? `${dateLabel(date)}, ${time.slice(0, 5)}`
                  : "Wybierz termin z kalendarza"}
              </strong>
            </div>
            <Button
              type="submit"
              disabled={
                !time ||
                slots.loading ||
                Boolean(slots.error) ||
                !slots.data?.includes(time)
              }
              busy={mutation.busy}
            >
              Potwierdź rezerwację
            </Button>
          </div>
        </form>
      ) : (
        <DataState state={reviews} empty="Ten lekarz nie ma jeszcze opinii">
          {(data) => <ReviewList reviews={data} />}
        </DataState>
      )}
    </Modal>
  );
}

export function Doctors() {
  const [filters, setFilters] = useState(() => {
    try {
      const cached = JSON.parse(
        sessionStorage.getItem("doctor-search") || "{}",
      );
      sessionStorage.removeItem("doctor-search");
      return cached;
    } catch {
      return {};
    }
  });
  const query = new URLSearchParams({
    q: filters.q || "",
    specialization: filters.specialization || "",
  });
  const state = useData(`/doctors?${query}`, []),
    specializations = useData("/specializations", []);
  const [selected, setSelected] = useState(null);
  function search(event) {
    event.preventDefault();
    setFilters(Object.fromEntries(new FormData(event.currentTarget)));
  }
  return (
    <>
      <PageHeading
        title="Znajdź swojego specjalistę"
        description="Wybierz lekarza, sprawdź opinie i zarezerwuj dogodny termin."
      />
      <section className="card search-card">
        <form className="search-form" onSubmit={search}>
          <Field
            label="Nazwisko lub imię lekarza"
            name="q"
            placeholder="Szukaj lekarza…"
            defaultValue={filters.q || ""}
          />
          <Select
            label="Specjalizacja"
            name="specialization"
            defaultValue={filters.specialization || ""}
          >
            <option value="">Wszystkie specjalizacje</option>
            {specializations.data?.map((s) => (
              <option value={s.id} key={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
          <Button type="submit">
            <Icon name="search" size={18} />
            Szukaj lekarza
          </Button>
        </form>
        <ErrorBox>{specializations.error}</ErrorBox>
      </section>
      <div className="section-heading">
        <h2>
          Nasi specjaliści{" "}
          <span className="count">{state.data?.length || 0}</span>
        </h2>
        <RefreshButton onClick={state.refresh} />
      </div>
      <DataState
        state={state}
        empty="Nie znaleźliśmy lekarzy dla tych kryteriów"
      >
        {(data) => (
          <div className="doctor-grid">
            {data.map((doctor) => (
              <DoctorCard
                key={doctor.id}
                doctor={doctor}
                onSelect={setSelected}
              />
            ))}
          </div>
        )}
      </DataState>
      {selected && (
        <DoctorModal doctor={selected} onClose={() => setSelected(null)} />
      )}
    </>
  );
}
