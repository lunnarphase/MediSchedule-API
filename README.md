# MediSchedule

Aplikacja webowa do rezerwacji wizyt lekarskich. Pacjent wyszukuje lekarza i rezerwuje wolny termin, lekarz układa grafik i prowadzi wizyty, a administrator zarządza kontami, wizytami, opiniami i bezpieczeństwem logowania.
## Technologie

- **Backend:** Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation, Mail, Actuator), Flyway
- **Baza danych:** PostgreSQL 16
- **Frontend:** React 19, Vite, serwowany przez Nginx
- **Dokumentacja API:** springdoc-openapi (Swagger UI)
- **Testy:** JUnit 5, Mockito, MockMvc, Testcontainers
- **Uruchomienie:** Docker Compose; lokalny serwer pocztowy Mailpit

## Uruchomienie

Wymagany jest Docker z Docker Compose.

1. Skopiuj plik konfiguracyjny:

   ```bash
   cp .env.example .env
   ```

2. W pliku `.env` ustaw co najmniej:
   - `DB_PASSWORD` – hasło do bazy danych (dowolne),
   - `DEMO_ENABLED=true` i `DEMO_PASSWORD`, jeśli aplikacja ma wystartować z kontami i danymi demonstracyjnymi. Hasło musi mieć 8–72 znaki, wielką i małą literę, cyfrę oraz znak specjalny.

3. Uruchom aplikację:

   ```bash
   docker compose up -d --build
   ```

4. Po starcie dostępne są:

   | Usługa | Adres |
   |---|---|
   | Aplikacja | http://localhost:8088 |
   | Swagger UI | http://localhost:8088/swagger-ui/index.html |
   | Mailpit (wysłane e-maile) | http://localhost:8025 |
   | Backend (bezpośrednio) | http://localhost:8081 |

   Porty można zmienić w `.env` (`FRONTEND_PORT`, `BACKEND_PORT`, `DB_PORT`, `MAILPIT_PORT`).

Zatrzymanie: `docker compose down`. Usunięcie także danych: `docker compose down -v`.

### Konta demonstracyjne

Gdy `DEMO_ENABLED=true`, przy pierwszym starcie na pustej bazie tworzone są konta (hasło dla wszystkich to `DEMO_PASSWORD`):

| Rola | E-mail |
|---|---|
| Administrator | admin@medischedule.local |
| Pacjent | patient@medischedule.local, patient2@medischedule.local |
| Lekarz | doctor@medischedule.local, doctor2@…, doctor3@…, doctor4@medischedule.local |

Dane demonstracyjne są zakładane tylko wtedy, gdy baza jest pusta. Późniejsza zmiana `DEMO_PASSWORD` nie zmienia haseł istniejących kont.

### Testy

```bash
docker compose --profile test run --rm tests
```

Testy integracyjne uruchamiają własną bazę PostgreSQL przez Testcontainers i nie korzystają z danych aplikacji.

## Główne funkcjonalności

**Pacjent**
- rejestracja i logowanie,
- wyszukiwanie lekarzy po nazwisku i specjalizacji,
- przegląd wolnych terminów i rezerwacja wizyty,
- anulowanie własnej wizyty, wgląd w zalecenia lekarza,
- opinia po zakończonej wizycie i zgłaszanie opinii do moderacji.

**Lekarz**
- grafik pracy na wybrane dni oraz szablony godzin,
- profil: opis, cena i czas trwania wizyty,
- zmiana statusu wizyty i wpisywanie zaleceń,
- podgląd opinii.

**Administrator**
- zarządzanie kontami: dodawanie, edycja, zmiana roli, blokowanie, usuwanie,
- edycja i usuwanie wizyt,
- moderacja opinii i zgłoszeń,
- historia logowań i blokad adresów IP,
- historia wysłanych e-maili ze stronicowaniem, filtrowaniem i ponawianiem wysyłki.

**Wszyscy użytkownicy:** powiadomienia w aplikacji, edycja profilu, zmiana hasła.

## Reguły biznesowe

- Wizyta musi w całości mieścić się w grafiku lekarza i zaczynać się w siatce jego czasu wizyty.
- Ani lekarz, ani pacjent nie może mieć dwóch nakładających się wizyt. Pilnuje tego serwis oraz ograniczenia `EXCLUDE` w PostgreSQL, także przy równoczesnych rezerwacjach.
- Lekarz nie może zapisać grafiku, który nie obejmuje istniejących rezerwacji.
- Wizytę można oznaczyć jako zakończoną dopiero po jej rozpoczęciu. Opinię wystawia tylko pacjent tej wizyty, raz, po jej zakończeniu.
- Zmiana czasu wizyty u lekarza nie zmienia długości wizyt już zarezerwowanych.
- Nie można zablokować, usunąć ani odebrać uprawnień ostatniemu aktywnemu administratorowi.
- Powtarzane nieudane logowania czasowo blokują konto, a z adresów publicznych także adres IP.

## Architektura

Jedna aplikacja backendowa z podziałem na warstwy:

```
backend/src/main/java/pl/medischedule/
├── web/          kontrolery REST, obsługa błędów, konfiguracja Swaggera
├── service/      logika biznesowa
├── repository/   dostęp do danych (Spring Data JPA)
├── domain/       encje JPA
└── security/     konfiguracja Spring Security, bieżący użytkownik
```

- **Uwierzytelnianie:** sesja (cookie `JSESSIONID`), hasła haszowane BCrypt, ochrona CSRF (token w cookie `XSRF-TOKEN`, nagłówek `X-XSRF-TOKEN`).
- **Autoryzacja:** role `patient`, `doctor`, `admin`. Rola i własność danych są sprawdzane w serwisach, np. pacjent anuluje tylko własną wizytę.
- **Schemat bazy:** migracje Flyway w `backend/src/main/resources/db/migration`.
- **Asynchroniczne e-maile:** rezerwacja zapisuje w tej samej transakcji zadania w tabeli `email_jobs`. Po zatwierdzeniu transakcji zdarzenie uruchamia osobny wątek (`@Async`), który wysyła wiadomości przez SMTP. Dodatkowo co kilka sekund scheduler sprawdza kolejkę. Nieudane wysyłki są ponawiane z rosnącym odstępem, a awaria serwera poczty nie blokuje rezerwacji.
- **Frontend:** komunikuje się z API przez Nginx pod tym samym adresem (`/api`).

## Dokumentacja

- **REST API:** Swagger UI pod `/swagger-ui/index.html`. Logowanie odbywa się sesją: najpierw `GET /api/auth/csrf`, potem `POST /api/auth/login`, a przed kolejnymi zapisami ponownie `GET /api/auth/csrf`.
- **Model danych:** [docs/ERD.md](docs/ERD.md)

## Struktura repozytorium

```
backend/        aplikacja Spring Boot
frontend/       aplikacja React i konfiguracja Nginx
docs/           diagram ERD
legacy/dotnet/  archiwum wcześniejszej wersji .NET (nieużywane przy uruchomieniu)
compose.yaml    konfiguracja Docker Compose
.env.example    przykładowa konfiguracja
```
