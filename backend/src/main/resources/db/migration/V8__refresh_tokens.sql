-- =============================================================
-- V8: refresh tokens (rotation + reuse detection + logout)
-- =============================================================
-- One row per refresh token we ever issued. The token itself is a signed
-- JWT; its "jti" claim is this row's id. We store no secret here: the
-- signature proves the token is ours, the row says whether it's still allowed.
--
-- family_id groups every token that came from ONE login (one device).
-- Each /refresh revokes the old token (ROTATED) and issues a new one in the
-- same family. If an already-rotated token shows up again, someone copied
-- it → we revoke the whole family (REUSE), logging that device out.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id            UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id     UUID        NOT NULL,
    expires_at    TIMESTAMP   NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    revoked_at    TIMESTAMP,
    revoke_reason VARCHAR(20)            -- ROTATED / LOGOUT / LOGOUT_ALL / REUSE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_active
    ON refresh_tokens(user_id) WHERE revoked_at IS NULL;
-- nightly cleanup deletes expired rows
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires ON refresh_tokens(expires_at);
