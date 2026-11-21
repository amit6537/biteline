-- =============================================================
-- V7 — Phase 12: Location & ETA
-- =============================================================

-- How long this kitchen usually takes (owner can change it). Used for ETA.
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS avg_prep_minutes INT NOT NULL DEFAULT 20;
ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS chk_restaurants_prep;
ALTER TABLE restaurants ADD CONSTRAINT chk_restaurants_prep CHECK (avg_prep_minutes BETWEEN 1 AND 180);

-- "Restaurants near me": a bounding-box filter on lat/lng can use this
-- index, so only nearby rows get the expensive Haversine calculation.
CREATE INDEX IF NOT EXISTS idx_restaurants_lat_lng ON restaurants(latitude, longitude);

-- Snapshot the drop location on the order (like delivery_address_text):
-- if the customer edits the saved address later, this order still knows
-- where it's going.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_lat DECIMAL(9,6);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_lng DECIMAL(9,6);
-- When the ETA was last recalculated (used to throttle GPS-driven updates)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS eta_updated_at TIMESTAMP;
