-- =============================================================
-- V6 — Phase 11: Reviews & Ratings
--
-- CONCEPT: Read-optimized aggregation
--   Restaurant lists show "4.3 ★ (1,204)". Computing AVG() over all reviews
--   on every list request would be slow. Instead we keep running totals ON
--   the restaurant row and update them when a review is written:
--     rating_sum    = sum of all food ratings      (exact integer, no drift)
--     total_reviews = how many reviews
--     avg_rating    = rating_sum / total_reviews   (rounded, for display/sort)
--   Reads become a plain column read. Writes do a little extra work.
-- =============================================================
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS rating_sum BIGINT NOT NULL DEFAULT 0;

-- Which partner delivered the reviewed order (for delivery ratings)
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS partner_id UUID REFERENCES users(id);
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS chk_reviews_comment_len;
ALTER TABLE reviews ADD CONSTRAINT chk_reviews_comment_len
    CHECK (comment IS NULL OR length(comment) <= 1000);

-- Restaurant page: newest reviews first
CREATE INDEX IF NOT EXISTS idx_reviews_restaurant_created ON reviews(restaurant_id, created_at DESC);
-- Partner's average delivery rating
CREATE INDEX IF NOT EXISTS idx_reviews_partner ON reviews(partner_id) WHERE delivery_rating IS NOT NULL;

-- Backfill the running totals from any reviews that already exist
UPDATE restaurants r
SET rating_sum    = s.total,
    total_reviews = s.cnt,
    avg_rating    = ROUND(s.total::numeric / s.cnt, 2)
FROM (SELECT restaurant_id, SUM(food_rating) AS total, COUNT(*) AS cnt
      FROM reviews GROUP BY restaurant_id) s
WHERE r.id = s.restaurant_id;
