-- =============================================================
-- V5: production hardening (email outbox, ShedLock, stuck orders)
-- =============================================================

-- ── ShedLock: one row per scheduled job = "who is running it, until when"
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- ── Email outbox: every notification also remembers its email state.
--   PENDING → SENT, or retried with backoff, or FAILED after max attempts.
--   Old rows become SKIPPED so we don't mass-email history on deploy.
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS email_status          VARCHAR(20) NOT NULL DEFAULT 'SKIPPED',
    ADD COLUMN IF NOT EXISTS email_attempts        INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS email_next_attempt_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS email_last_error      TEXT,
    ADD COLUMN IF NOT EXISTS email_sent_at         TIMESTAMP;

-- The dispatcher only ever looks at PENDING rows that are due
CREATE INDEX IF NOT EXISTS idx_notifications_email_due
    ON notifications(email_next_attempt_at) WHERE email_status = 'PENDING';

-- ── Reject cooldown: WHEN was an assignment cancelled?
--   A partner who rejected an order may be asked again after a cooldown,
--   so an order can't be stuck forever just because everyone said no once.
ALTER TABLE delivery_assignments ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
UPDATE delivery_assignments SET cancelled_at = assigned_at
    WHERE status = 'CANCELLED' AND cancelled_at IS NULL;

-- ── Stuck-order alert sent? (alert ops only ONCE per order)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS assignment_alerted_at TIMESTAMP;
