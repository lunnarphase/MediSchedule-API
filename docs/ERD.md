# Model danych

```mermaid
erDiagram
    roles ||--o{ users : rola
    users ||--o| patients : profil
    users ||--o| doctors : profil
    patients ||--o{ appointments : rezerwacje
    doctors ||--o{ appointments : konsultacje
    doctors ||--o{ doctor_availability : grafik
    doctors ||--o{ doctor_schedule_templates : szablony
    doctors ||--o{ doctors_specializations : specjalizacje
    specializations ||--o{ doctors_specializations : lekarze
    appointments ||--o| reviews : ocena
    patients ||--o{ reviews : autor
    doctors ||--o{ reviews : oceniany
    reviews ||--o{ review_reports : zgloszenia
    users ||--o{ review_reports : zglaszajacy
    users ||--o{ notifications : odbiorca
    users ||--o{ email_jobs : odbiorca
    appointments |o--o{ email_jobs : potwierdzenie

    users {
        bigint id PK
        bigint id_role FK
        string email UK
        string password
        boolean blocked
    }
    patients {
        bigint id PK
        bigint id_user FK,UK
        string pesel UK
    }
    doctors {
        bigint id PK
        bigint id_user FK,UK
        int visit_duration
        decimal visit_price
    }
    appointments {
        bigint id PK
        bigint id_patient FK
        bigint id_doctor FK
        timestamp starts_at
        timestamp ends_at
        string status
        boolean review_submitted
    }
    reviews {
        bigint id PK
        bigint id_appointment FK,UK
        bigint id_patient FK
        bigint id_doctor FK
        int rating
        boolean deleted
    }
    email_jobs {
        bigint id PK
        bigint id_user FK
        bigint id_appointment FK
        string status
        int attempts
        timestamp next_attempt_at
        timestamp lease_until
    }
    login_attempts {
        bigint id PK
        string email
        string ip_address
        timestamp attempted_at
        boolean success
    }
    blocked_ips {
        string ip_address PK
        timestamp blocked_until
        string reason
    }
```

Encji JPA jest 14. `doctors_specializations` to tabela relacji wiele-do-wielu. `login_attempts` i `blocked_ips` są niezależnymi rejestrami technicznymi. `notifications.related_id` wskazuje kontekst powiadomienia, nie jest kluczem obcym. `flyway_schema_history` przechowuje wersje migracji i nie należy do modelu biznesowego.

`appointments.starts_at` i `ends_at` zapisują rzeczywisty przedział rezerwacji. Dwa ograniczenia `EXCLUDE USING gist` nie pozwalają na nachodzące nieanulowane wizyty tego samego lekarza ani pacjenta. Unikalne `reviews.id_appointment` ogranicza wizytę do jednej opinii. Przy usunięciu wizyty powiązanie `email_jobs.id_appointment` jest zerowane; usunięcie konta usuwa jego zadania pocztowe.
