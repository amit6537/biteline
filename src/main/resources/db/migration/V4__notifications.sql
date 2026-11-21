-- =============================================================
-- Phase 10: Notifications (in-app inbox)
--
-- Every Kafka event that a person should know about becomes a row here:
-- "Order accepted", "New order received", "Rahul is picking up your food"...
-- The app shows them in a bell-icon inbox; email/push are extra channels.
-- =============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- CASCADE: deleting a user deletes their inbox too
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(150) NOT NULL,
    message     TEXT         NOT NULL,
    -- No FK on purpose: a notification is a historical message and
    -- should survive even if the order is ever removed.
    order_id    UUID,
    -- WHY event_key? Kafka delivers "at least once": after a crash or
    -- rebalance the SAME event can arrive twice. The UNIQUE rule below makes
    -- the second insert a no-op, so nobody gets "Order accepted" twice.
    event_key   VARCHAR(200) NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    -- When the EVENT happened (not when we processed it): keeps the inbox in
    -- the right order even if consumers fall behind.
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    read_at     TIMESTAMP,
    CONSTRAINT uq_notifications_user_event UNIQUE (user_id, event_key)
);

-- Inbox: my notifications, newest first
CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications(user_id, created_at DESC);

-- Bell badge: "how many unread?" — tiny index, only unread rows
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications(user_id) WHERE is_read = FALSE;
