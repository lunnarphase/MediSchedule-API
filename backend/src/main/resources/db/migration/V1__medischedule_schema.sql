CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE specializations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL CHECK (position('@' in email) > 1),
    password VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL CHECK (char_length(trim(username)) >= 3),
    id_role BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE login_attempts (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_login_attempts_email ON login_attempts(email);
CREATE INDEX idx_login_attempts_attempted_at ON login_attempts(attempted_at);

CREATE TABLE blocked_ips (
    ip_address VARCHAR(45) PRIMARY KEY,
    blocked_until TIMESTAMPTZ,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_blocked_ips_blocked_until ON blocked_ips(blocked_until);

CREATE TABLE patients (
    id BIGSERIAL PRIMARY KEY,
    id_user BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pesel CHAR(11) UNIQUE NOT NULL CHECK (pesel ~ '^[0-9]{11}$'),
    phone VARCHAR(20)
);

CREATE TABLE doctors (
    id BIGSERIAL PRIMARY KEY,
    id_user BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bio TEXT,
    visit_price DECIMAL(10,2) CHECK (visit_price IS NULL OR visit_price >= 0),
    visit_duration INTEGER DEFAULT 30 CHECK (visit_duration BETWEEN 10 AND 240)
);

CREATE TABLE doctors_specializations (
    id_doctor BIGINT REFERENCES doctors(id) ON DELETE CASCADE,
    id_specialization BIGINT REFERENCES specializations(id) ON DELETE CASCADE,
    PRIMARY KEY (id_doctor, id_specialization)
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    id_user BIGINT REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    type VARCHAR(50) NOT NULL DEFAULT 'general' CHECK (char_length(trim(type)) > 0),
    related_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notifications_user_created_at ON notifications(id_user, created_at DESC);
CREATE INDEX idx_notifications_user_type_created_at ON notifications(id_user, type, created_at DESC);
CREATE INDEX idx_notifications_user_is_read ON notifications(id_user, is_read);

CREATE TABLE appointments (
    id BIGSERIAL PRIMARY KEY,
    id_patient BIGINT REFERENCES patients(id) ON DELETE CASCADE,
    id_doctor BIGINT REFERENCES doctors(id) ON DELETE CASCADE,
    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    CHECK (starts_at < ends_at),
    status VARCHAR(50) NOT NULL DEFAULT 'confirmed' CHECK (status IN ('confirmed', 'completed', 'cancelled', 'noshow')),
    recommendations TEXT,
    cancel_reason TEXT,
    cancel_comment TEXT,
    review_submitted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_appointments_active_slot
ON appointments (id_doctor, appointment_date, appointment_time)
WHERE status <> 'cancelled';

CREATE TABLE doctor_availability (
    id BIGSERIAL PRIMARY KEY,
    id_doctor BIGINT NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    CHECK (start_time < end_time)
);

CREATE TABLE doctor_schedule_templates (
    id BIGSERIAL PRIMARY KEY,
    id_doctor BIGINT NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL CHECK (char_length(trim(name)) > 0),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    CHECK (start_time < end_time)
);

CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    id_appointment BIGINT UNIQUE NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    id_doctor BIGINT NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    id_patient BIGINT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE review_reports (
    id BIGSERIAL PRIMARY KEY,
    id_review BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    id_reporter BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'dismissed', 'resolved')),
    admin_response TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE appointments ADD CONSTRAINT no_overlapping_doctor_appointments
    EXCLUDE USING gist (id_doctor WITH =, tsrange(starts_at, ends_at, '[)') WITH &&)
    WHERE (status <> 'cancelled');
ALTER TABLE appointments ADD CONSTRAINT no_overlapping_patient_appointments
    EXCLUDE USING gist (id_patient WITH =, tsrange(starts_at, ends_at, '[)') WITH &&)
    WHERE (status <> 'cancelled');
CREATE INDEX idx_availability_doctor_date ON doctor_availability(id_doctor,date);
CREATE INDEX idx_appointments_patient_date ON appointments(id_patient,appointment_date);
CREATE TABLE email_jobs (
    id BIGSERIAL PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','retry','sent','failed')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    last_error TEXT
);
CREATE INDEX idx_email_jobs_pending ON email_jobs(next_attempt_at) WHERE status IN ('pending','retry');
INSERT INTO roles(name) VALUES ('admin'),('doctor'),('patient');
INSERT INTO specializations(name) VALUES ('Kardiologia'),('Pediatria'),('Neurologia'),('Medycyna rodzinna');
