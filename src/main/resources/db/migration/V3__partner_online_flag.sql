-- =============================================================
-- Phase 8 fix: separate the partner's WISH from their STATE.
--
--   is_online    = what the partner chose ("I want orders" / "I'm done for today")
--   is_available = what the system uses for assignment (online AND not busy)
--
-- Before, there was only is_available. If a partner went offline in the middle
-- of a delivery, finishing that delivery flipped them back to available and
-- they kept getting new orders. Now, after a delivery: is_available = is_online.
-- =============================================================
ALTER TABLE delivery_partner_status
    ADD COLUMN IF NOT EXISTS is_online BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing rows: whoever is available right now is clearly online
UPDATE delivery_partner_status SET is_online = is_available;
