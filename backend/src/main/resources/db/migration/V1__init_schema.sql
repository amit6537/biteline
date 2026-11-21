-- =============================================================
-- Food Delivery Backend — Database Schema
-- Phase 1: Foundation
--
-- Design decisions explained inline.
-- =============================================================

-- Enable the uuid-ossp extension so PostgreSQL can generate UUIDs.
-- WHY UUID over auto-increment INT?
--   → UUIDs are globally unique — safe to generate in the app layer
--     before inserting (no round-trip to DB needed).
--   → In a distributed system, two services can create IDs independently
--     with zero collision risk.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================
-- USERS
-- A single users table with a role discriminator.
--
-- WHY one table instead of separate customer/partner tables?
--   → Simplicity at this stage. A user can hold multiple roles
--     (e.g. a restaurant owner who also orders food).
--   → We use a role enum to control access in the app layer.
-- =============================================================
CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name          VARCHAR(100)        NOT NULL,
    email         VARCHAR(150)        NOT NULL UNIQUE,
    phone         VARCHAR(20)         NOT NULL UNIQUE,
    password_hash TEXT                NOT NULL,
    role          VARCHAR(30)         NOT NULL DEFAULT 'CUSTOMER',
    -- role values: CUSTOMER | RESTAURANT_OWNER | DELIVERY_PARTNER | ADMIN
    is_active     BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- =============================================================
-- ADDRESSES
-- A user can have multiple saved addresses (home, work, etc.)
--
-- WHY store lat/lng?
--   → For delivery radius checks and ETA calculation in Phase 12.
--   → Geocoding (address → lat/lng) would happen in the app before saving.
-- =============================================================
CREATE TABLE IF NOT EXISTS addresses (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label       VARCHAR(50)  NOT NULL DEFAULT 'Home',   -- "Home", "Work", "Other"
    street      TEXT         NOT NULL,
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    pincode     VARCHAR(10)  NOT NULL,
    latitude    DECIMAL(9,6),                            -- e.g. 19.075984
    longitude   DECIMAL(9,6),                            -- e.g. 72.877656
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- =============================================================
-- RESTAURANTS
-- Owned by a user with role RESTAURANT_OWNER.
--
-- WHY store lat/lng on restaurant?
--   → To find restaurants within delivery radius of a customer's address.
-- =============================================================
CREATE TABLE IF NOT EXISTS restaurants (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id            UUID            NOT NULL REFERENCES users(id),
    name                VARCHAR(150)    NOT NULL,
    description         TEXT,
    cuisine_type        VARCHAR(100),
    image_url           TEXT,
    avg_rating          DECIMAL(3,2)    NOT NULL DEFAULT 0.00,
    total_reviews       INT             NOT NULL DEFAULT 0,
    is_open             BOOLEAN         NOT NULL DEFAULT TRUE,
    delivery_radius_km  DECIMAL(5,2)    NOT NULL DEFAULT 5.00,
    min_order_amount    DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    latitude            DECIMAL(9,6)    NOT NULL,
    longitude           DECIMAL(9,6)    NOT NULL,
    city                VARCHAR(100)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- =============================================================
-- MENU ITEMS
-- Each item belongs to one restaurant.
--
-- WHY store price here AND snapshot it in order_items?
--   → Price can change over time. An order placed last month must
--     always reflect the price AT that moment, not today's price.
--   → This is called a "price snapshot" — a critical e-commerce pattern.
-- =============================================================
CREATE TABLE IF NOT EXISTS menu_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    restaurant_id   UUID            NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name            VARCHAR(150)    NOT NULL,
    description     TEXT,
    price           DECIMAL(10,2)   NOT NULL,
    category        VARCHAR(100)    NOT NULL DEFAULT 'Main Course',
    image_url       TEXT,
    is_available    BOOLEAN         NOT NULL DEFAULT TRUE,
    is_vegetarian   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- =============================================================
-- CART ITEMS
-- A temporary holding area before checkout.
--
-- WHY a separate cart table instead of a "cart" entity?
--   → A cart is just a collection of items tied to a user.
--     No need for a parent "cart" row — user_id IS the cart key.
--   → One user = one active cart at a time (enforced by UNIQUE).
--
-- WHY NOT persist cart in Redis only?
--   → DB cart survives app restarts and is queryable.
--     We'll add Redis caching on top later (Phase 9), but the
--     source of truth stays in PostgreSQL.
-- =============================================================
CREATE TABLE IF NOT EXISTS cart_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    menu_item_id    UUID    NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    quantity        INT     NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    -- A user can only have one cart entry per menu item
    UNIQUE (user_id, menu_item_id)
);

-- =============================================================
-- ORDERS
-- The central entity of the whole system.
--
-- WHY store delivery_address as a snapshot (JSON/text) vs FK?
--   → Option A (FK): If user later edits/deletes the address,
--     the order loses its delivery location. BAD.
--   → Option B (snapshot): We copy address data at order time.
--     The order always has the address it was placed with. GOOD.
--   → We use a FK here BUT will snapshot address fields in
--     the service layer as well (belt + suspenders approach).
--
-- Status transitions (State Machine — Phase 5):
--   PLACED → ACCEPTED → PREPARING → READY → PICKED_UP → DELIVERED
--   Any state → CANCELLED (with business rules)
-- =============================================================
CREATE TABLE IF NOT EXISTS orders (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id             UUID            NOT NULL REFERENCES users(id),
    restaurant_id           UUID            NOT NULL REFERENCES restaurants(id),
    delivery_address_id     UUID            NOT NULL REFERENCES addresses(id),
    -- Snapshot the address text at order time (defensive copy)
    delivery_address_text   TEXT            NOT NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'PLACED',
    -- status values: PLACED | ACCEPTED | PREPARING | READY | PICKED_UP | DELIVERED | CANCELLED
    subtotal                DECIMAL(10,2)   NOT NULL,
    delivery_fee            DECIMAL(10,2)   NOT NULL DEFAULT 30.00,
    total_amount            DECIMAL(10,2)   NOT NULL,
    special_instructions    TEXT,
    placed_at               TIMESTAMP       NOT NULL DEFAULT NOW(),
    accepted_at             TIMESTAMP,
    ready_at                TIMESTAMP,
    delivered_at            TIMESTAMP,
    cancelled_at            TIMESTAMP,
    cancellation_reason     TEXT,
    estimated_delivery_at   TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- =============================================================
-- ORDER ITEMS
-- The line items of an order — a price snapshot of each item.
--
-- WHY price_at_time?
--   → See menu_items comment above. This is the price the customer
--     actually paid for this specific item in this specific order.
-- =============================================================
CREATE TABLE IF NOT EXISTS order_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id        UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id    UUID            NOT NULL REFERENCES menu_items(id),
    name_at_time    VARCHAR(150)    NOT NULL,    -- snapshot of item name
    price_at_time   DECIMAL(10,2)   NOT NULL,    -- snapshot of item price
    quantity        INT             NOT NULL CHECK (quantity > 0),
    item_total      DECIMAL(10,2)   NOT NULL     -- price_at_time * quantity
);

-- =============================================================
-- PAYMENTS
-- One payment per order.
--
-- WHY a separate table instead of columns on orders?
--   → Separation of concerns. Payment has its own lifecycle:
--     PENDING → SUCCESS / FAILED → REFUNDED.
--   → In Phase 6 we'll implement idempotency — the transaction_id
--     is the external payment gateway's reference.
-- =============================================================
CREATE TABLE IF NOT EXISTS payments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id        UUID            NOT NULL UNIQUE REFERENCES orders(id),
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    -- status values: PENDING | SUCCESS | FAILED | REFUNDED
    amount          DECIMAL(10,2)   NOT NULL,
    method          VARCHAR(20)     NOT NULL DEFAULT 'CASH',
    -- method values: CARD | UPI | CASH | WALLET
    transaction_id  VARCHAR(255),               -- gateway's reference ID
    idempotency_key VARCHAR(255)    UNIQUE,      -- prevents double-charge
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- =============================================================
-- DELIVERY ASSIGNMENTS
-- Links an order to a delivery partner.
--
-- WHY a separate table?
--   → An order could theoretically be reassigned (partner cancels).
--   → Keeping history of assignments is useful for analytics.
--   → Keeps the orders table clean.
-- =============================================================
CREATE TABLE IF NOT EXISTS delivery_assignments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id        UUID        NOT NULL REFERENCES orders(id),
    partner_id      UUID        NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    -- status values: ASSIGNED | PICKED_UP | DELIVERED | CANCELLED
    assigned_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    picked_up_at    TIMESTAMP,
    delivered_at    TIMESTAMP
);

-- =============================================================
-- REVIEWS
-- A customer can review an order only after it is DELIVERED.
-- This constraint is enforced in the service layer (Phase 11).
--
-- WHY tie review to order (not just restaurant)?
--   → One customer, one review per order — prevents spam.
--   → Traceability: we know exactly which delivery experience
--     the review is about.
-- =============================================================
CREATE TABLE IF NOT EXISTS reviews (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id        UUID    NOT NULL UNIQUE REFERENCES orders(id),
    customer_id     UUID    NOT NULL REFERENCES users(id),
    restaurant_id   UUID    NOT NULL REFERENCES restaurants(id),
    food_rating     INT     NOT NULL CHECK (food_rating BETWEEN 1 AND 5),
    delivery_rating INT     CHECK (delivery_rating BETWEEN 1 AND 5),
    comment         TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =============================================================
-- DELIVERY PARTNER AVAILABILITY
-- Tracks whether a delivery partner is online and free.
--
-- WHY a separate table?
--   → This data changes constantly (partner goes online/offline).
--   → Isolating it makes it easy to cache in Redis (Phase 9).
-- =============================================================
CREATE TABLE IF NOT EXISTS delivery_partner_status (
    partner_id      UUID PRIMARY KEY REFERENCES users(id),
    is_available    BOOLEAN     NOT NULL DEFAULT FALSE,
    current_lat     DECIMAL(9,6),
    current_lng     DECIMAL(9,6),
    last_seen_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- =============================================================
-- INDEXES
-- Always add indexes on columns you filter/join on frequently.
--
-- WHY do indexes matter?
--   → Without an index, PostgreSQL does a full table scan (O(n)).
--   → With a B-tree index, lookups are O(log n).
--   → For large tables (orders, menu_items) this is the difference
--     between 2ms and 2 seconds.
-- =============================================================

-- Users: fast login lookup by email
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Restaurants: filter by city + open status
CREATE INDEX IF NOT EXISTS idx_restaurants_city ON restaurants(city);
CREATE INDEX IF NOT EXISTS idx_restaurants_city_open ON restaurants(city, is_open);

-- Menu items: load a restaurant's menu
CREATE INDEX IF NOT EXISTS idx_menu_items_restaurant ON menu_items(restaurant_id);

-- Cart: load a user's cart
CREATE INDEX IF NOT EXISTS idx_cart_items_user ON cart_items(user_id);

-- Orders: customer order history
CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
-- Orders: restaurant dashboard
CREATE INDEX IF NOT EXISTS idx_orders_restaurant ON orders(restaurant_id);
-- Orders: filter by status (e.g. all PLACED orders)
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

-- Reviews: restaurant's reviews
CREATE INDEX IF NOT EXISTS idx_reviews_restaurant ON reviews(restaurant_id);

