-- =============================================================
-- V9: password reset ("forgot password")
-- =============================================================
-- The user gets a link with a random one-time token by email. We store
-- only its SHA-256 hash: someone who reads this table (a backup, a leaked
-- dump) still can't reset anybody's password with it.
--
-- used_at is set when the token is used OR replaced by a newer request,
-- so only the latest link works and every link works once.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,      -- hex SHA-256 of the token
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    used_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_user_open
    ON password_reset_tokens(user_id) WHERE used_at IS NULL;
-- nightly cleanup deletes expired rows
CREATE INDEX IF NOT EXISTS idx_password_reset_expires ON password_reset_tokens(expires_at);
