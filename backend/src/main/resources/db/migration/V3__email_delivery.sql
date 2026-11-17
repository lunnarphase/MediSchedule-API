CREATE TABLE email_jobs (
    id BIGSERIAL PRIMARY KEY,
    id_user BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id_appointment BIGINT REFERENCES appointments(id) ON DELETE SET NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'processing', 'sent', 'failed')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    UNIQUE (id_user, id_appointment)
);
CREATE INDEX idx_email_jobs_due ON email_jobs(next_attempt_at, id)
    WHERE status IN ('pending', 'processing');
CREATE INDEX idx_email_jobs_created ON email_jobs(created_at DESC, id DESC);
