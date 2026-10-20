BEGIN;

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login VARCHAR(50) NOT NULL UNIQUE CHECK (btrim(login) <> ''),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (btrim(email) <> ''),
    hash_password TEXT NOT NULL CHECK (hash_password <> ''),
    contact_phone_number VARCHAR(32)
);

CREATE TABLE requisites (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    bank VARCHAR(255) NOT NULL CHECK (btrim(bank) <> ''),
    phone_number VARCHAR(32) NOT NULL CHECK (btrim(phone_number) <> ''),
    is_temporary BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE buy_request_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE assigned_buy_request_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE purchase_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE buy_item_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE approval_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE units (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE replacement_rules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE buy_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    comment TEXT,
    valid_until TIMESTAMPTZ NOT NULL,
    status_id BIGINT NOT NULL REFERENCES buy_request_statuses (id),
    total_amount_due NUMERIC(14, 2) CHECK (total_amount_due >= 0),
    payment_link TEXT,
    is_paid_back BOOLEAN NOT NULL DEFAULT FALSE,
    paid_back_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (valid_until > created_at),
    CHECK (updated_at >= created_at),
    CHECK ((is_paid_back AND paid_back_at IS NOT NULL)
        OR (NOT is_paid_back AND paid_back_at IS NULL)),
    CHECK (paid_back_at IS NULL OR paid_back_at >= created_at)
);

CREATE TABLE purchase (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shopper_id BIGINT NOT NULL REFERENCES users (id),
    receipt_photo_url TEXT,
    status_id BIGINT NOT NULL REFERENCES purchase_statuses (id),
    requisite_id BIGINT NOT NULL REFERENCES requisites (id),
    planned_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (completed_at IS NULL OR completed_at >= created_at),
    CHECK (updated_at >= created_at)
);

CREATE TABLE assigned_buy_requests (
    buy_request_id BIGINT NOT NULL REFERENCES buy_requests (id) ON DELETE CASCADE,
    purchase_id BIGINT NOT NULL REFERENCES purchase (id) ON DELETE CASCADE,
    status_id BIGINT NOT NULL REFERENCES assigned_buy_request_statuses (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (buy_request_id, purchase_id),
    CHECK (updated_at >= created_at)
);

CREATE TABLE buy_requests_recipients (
    buy_request_id BIGINT NOT NULL REFERENCES buy_requests (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (buy_request_id, user_id),
    CHECK (updated_at >= created_at)
);

CREATE TABLE buy_request_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    buy_request_id BIGINT NOT NULL REFERENCES buy_requests (id) ON DELETE CASCADE,
    item_name VARCHAR(255) NOT NULL CHECK (btrim(item_name) <> ''),
    actual_name VARCHAR(255),
    buy_status_id BIGINT NOT NULL REFERENCES buy_item_statuses (id),
    approval_status_id BIGINT NOT NULL REFERENCES approval_statuses (id),
    comment TEXT,
    requested_quantity NUMERIC(12, 3) NOT NULL CHECK (requested_quantity > 0),
    actual_quantity NUMERIC(12, 3) CHECK (actual_quantity >= 0),
    unit_id BIGINT NOT NULL REFERENCES units (id),
    item_photo_url TEXT,
    actual_price NUMERIC(14, 2) CHECK (actual_price >= 0),
    decision_at TIMESTAMPTZ
);

CREATE TABLE item_requirements (
    buy_request_item_id BIGINT PRIMARY KEY
        REFERENCES buy_request_items (id) ON DELETE CASCADE,
    max_unit_price NUMERIC(14, 2) CHECK (max_unit_price >= 0),
    max_total_price NUMERIC(14, 2) CHECK (max_total_price >= 0),
    replacement_policy_id BIGINT NOT NULL REFERENCES replacement_rules (id),
    quantity_tolerance NUMERIC(12, 3) NOT NULL DEFAULT 0 CHECK (quantity_tolerance >= 0)
);

CREATE INDEX idx_requisites_user_id ON requisites (user_id);
CREATE INDEX idx_buy_requests_user_id ON buy_requests (user_id);
CREATE INDEX idx_buy_requests_status_id ON buy_requests (status_id);
CREATE INDEX idx_buy_requests_valid_until ON buy_requests (valid_until);
CREATE INDEX idx_purchase_shopper_id ON purchase (shopper_id);
CREATE INDEX idx_purchase_status_id ON purchase (status_id);
CREATE INDEX idx_purchase_requisite_id ON purchase (requisite_id);
CREATE INDEX idx_assigned_buy_requests_purchase_id ON assigned_buy_requests (purchase_id);
CREATE INDEX idx_assigned_buy_requests_status_id ON assigned_buy_requests (status_id);
CREATE INDEX idx_buy_requests_recipients_user_id ON buy_requests_recipients (user_id);
CREATE INDEX idx_buy_request_items_buy_request_id ON buy_request_items (buy_request_id);
CREATE INDEX idx_buy_request_items_buy_status_id ON buy_request_items (buy_status_id);
CREATE INDEX idx_buy_request_items_approval_status_id ON buy_request_items (approval_status_id);
CREATE INDEX idx_buy_request_items_unit_id ON buy_request_items (unit_id);
CREATE INDEX idx_item_requirements_replacement_policy_id ON item_requirements (replacement_policy_id);

-- триггеры на updated_at
CREATE FUNCTION set_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_buy_requests_updated_at
    BEFORE UPDATE ON buy_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_purchase_updated_at
    BEFORE UPDATE ON purchase
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_assigned_buy_requests_updated_at
    BEFORE UPDATE ON assigned_buy_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_buy_requests_recipients_updated_at
    BEFORE UPDATE ON buy_requests_recipients
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMIT;
