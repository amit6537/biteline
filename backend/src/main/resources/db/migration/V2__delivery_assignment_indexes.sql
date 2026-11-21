-- =============================================================
-- Phase 8: Delivery Assignment
--
-- The tables delivery_assignments and delivery_partner_status were
-- created in V1. Here we add the indexes and safety rules Phase 8 needs.
-- =============================================================

-- SAFETY RULE: an order can have at most ONE active assignment.
-- "Partial unique index" = the UNIQUE rule only applies to rows matching
-- the WHERE clause. Old CANCELLED/DELIVERED rows are history and don't count.
-- Even if two threads try to assign the same order at the same moment,
-- the database itself rejects the second insert.
CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_assignments_active_order
    ON delivery_assignments(order_id)
    WHERE status IN ('ASSIGNED', 'PICKED_UP');

-- Same idea for partners: one active delivery per partner at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_assignments_active_partner
    ON delivery_assignments(partner_id)
    WHERE status IN ('ASSIGNED', 'PICKED_UP');

-- Partner's delivery history (newest first)
CREATE INDEX IF NOT EXISTS idx_delivery_assignments_partner
    ON delivery_assignments(partner_id, assigned_at DESC);

-- "Who is online right now?" — only scans available partners
CREATE INDEX IF NOT EXISTS idx_partner_status_available
    ON delivery_partner_status(is_available)
    WHERE is_available = TRUE;
