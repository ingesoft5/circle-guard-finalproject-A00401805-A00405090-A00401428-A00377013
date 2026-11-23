CREATE TABLE form_event_outbox (
    id UUID PRIMARY KEY,
    topic VARCHAR(64) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    traceparent VARCHAR(128),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_form_event_outbox_due ON form_event_outbox (next_attempt_at, created_at);
