BEGIN;

SELECT pg_advisory_xact_lock(726491230985117603);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login VARCHAR(50) NOT NULL UNIQUE CHECK (btrim(login) <> ''),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (btrim(email) <> ''),
    hash_password TEXT NOT NULL CHECK (hash_password <> ''),
    contact_phone_number VARCHAR(32),
    avatar_url TEXT
);

CREATE TABLE IF NOT EXISTS friends (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    friend_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, friend_id),
    CHECK (user_id < friend_id)
);

CREATE TABLE IF NOT EXISTS friend_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (sender_id <> recipient_id)
);

ALTER TABLE friend_requests ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'));
ALTER TABLE friend_requests ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;
ALTER TABLE friend_requests DROP CONSTRAINT IF EXISTS chk_friend_requests_resolution;
ALTER TABLE friend_requests ADD CONSTRAINT chk_friend_requests_resolution CHECK (
    (status = 'PENDING' AND resolved_at IS NULL)
    OR (status <> 'PENDING' AND resolved_at IS NOT NULL AND resolved_at >= created_at)
);

DROP INDEX IF EXISTS idx_friend_requests_pair;
CREATE UNIQUE INDEX IF NOT EXISTS idx_friend_requests_pending_pair
    ON friend_requests (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id))
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_friend_requests_sender_id ON friend_requests (sender_id);
CREATE INDEX IF NOT EXISTS idx_friend_requests_recipient_id ON friend_requests (recipient_id);

CREATE TABLE IF NOT EXISTS requisites (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    bank VARCHAR(255) NOT NULL CHECK (btrim(bank) <> ''),
    phone_number VARCHAR(32) NOT NULL CHECK (btrim(phone_number) <> ''),
    is_temporary BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS default_requisite_id BIGINT
    REFERENCES requisites (id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS buy_request_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS assigned_buy_request_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS purchase_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS buy_item_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS approval_statuses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS units (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS replacement_rules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

INSERT INTO buy_request_statuses (code, name) VALUES ('PENDING', 'Ожидает покупки') ON CONFLICT DO NOTHING;
INSERT INTO buy_item_statuses (code, name) VALUES ('PENDING', 'Ожидает покупки') ON CONFLICT DO NOTHING;
INSERT INTO approval_statuses (code, name) VALUES ('NOT_REQUIRED', 'Не требуется') ON CONFLICT DO NOTHING;
INSERT INTO units (code, name) VALUES
    ('PCS', 'шт.'), ('KG', 'кг'), ('G', 'г'), ('L', 'л'), ('ML', 'мл'), ('PACK', 'упак.')
ON CONFLICT DO NOTHING;
UPDATE replacement_rules SET name = 'Не покупать' WHERE name = 'Не заменять';
INSERT INTO replacement_rules (name, description) VALUES
    ('Не покупать', 'Если товара нет, пропустить его.'),
    ('Согласовать замену', 'Перед заменой уточнить у автора запроса.'),
    ('Можно заменить аналогом', 'Можно выбрать похожий товар в пределах указанного бюджета.')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS buy_requests (
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

CREATE TABLE IF NOT EXISTS purchase (
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

CREATE TABLE IF NOT EXISTS assigned_buy_requests (
    buy_request_id BIGINT NOT NULL REFERENCES buy_requests (id) ON DELETE CASCADE,
    purchase_id BIGINT NOT NULL REFERENCES purchase (id) ON DELETE CASCADE,
    status_id BIGINT NOT NULL REFERENCES assigned_buy_request_statuses (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (buy_request_id, purchase_id),
    CHECK (updated_at >= created_at)
);

CREATE TABLE IF NOT EXISTS buy_requests_recipients (
    buy_request_id BIGINT NOT NULL REFERENCES buy_requests (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (buy_request_id, user_id),
    CHECK (updated_at >= created_at)
);

CREATE TABLE IF NOT EXISTS buy_request_items (
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

CREATE TABLE IF NOT EXISTS item_requirements (
    buy_request_item_id BIGINT PRIMARY KEY
        REFERENCES buy_request_items (id) ON DELETE CASCADE,
    max_unit_price NUMERIC(14, 2) CHECK (max_unit_price >= 0),
    max_total_price NUMERIC(14, 2) CHECK (max_total_price >= 0),
    replacement_policy_id BIGINT NOT NULL REFERENCES replacement_rules (id),
    quantity_tolerance NUMERIC(12, 3) NOT NULL DEFAULT 0 CHECK (quantity_tolerance >= 0)
);

CREATE INDEX IF NOT EXISTS idx_friends_friend_id ON friends (friend_id);
CREATE INDEX IF NOT EXISTS idx_requisites_user_id ON requisites (user_id);
CREATE INDEX IF NOT EXISTS idx_buy_requests_user_id ON buy_requests (user_id);
CREATE INDEX IF NOT EXISTS idx_buy_requests_status_id ON buy_requests (status_id);
CREATE INDEX IF NOT EXISTS idx_buy_requests_valid_until ON buy_requests (valid_until);
CREATE INDEX IF NOT EXISTS idx_purchase_shopper_id ON purchase (shopper_id);
CREATE INDEX IF NOT EXISTS idx_purchase_status_id ON purchase (status_id);
CREATE INDEX IF NOT EXISTS idx_purchase_requisite_id ON purchase (requisite_id);
CREATE INDEX IF NOT EXISTS idx_assigned_buy_requests_purchase_id ON assigned_buy_requests (purchase_id);
CREATE INDEX IF NOT EXISTS idx_assigned_buy_requests_status_id ON assigned_buy_requests (status_id);
CREATE INDEX IF NOT EXISTS idx_buy_requests_recipients_user_id ON buy_requests_recipients (user_id);
CREATE INDEX IF NOT EXISTS idx_buy_request_items_buy_request_id ON buy_request_items (buy_request_id);
CREATE INDEX IF NOT EXISTS idx_buy_request_items_buy_status_id ON buy_request_items (buy_status_id);
CREATE INDEX IF NOT EXISTS idx_buy_request_items_approval_status_id ON buy_request_items (approval_status_id);
CREATE INDEX IF NOT EXISTS idx_buy_request_items_unit_id ON buy_request_items (unit_id);
CREATE INDEX IF NOT EXISTS idx_item_requirements_replacement_policy_id ON item_requirements (replacement_policy_id);

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_buy_requests_updated_at ON buy_requests;
CREATE TRIGGER trg_buy_requests_updated_at
    BEFORE UPDATE ON buy_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_purchase_updated_at ON purchase;
CREATE TRIGGER trg_purchase_updated_at
    BEFORE UPDATE ON purchase
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_assigned_buy_requests_updated_at ON assigned_buy_requests;
CREATE TRIGGER trg_assigned_buy_requests_updated_at
    BEFORE UPDATE ON assigned_buy_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
DROP TRIGGER IF EXISTS trg_buy_requests_recipients_updated_at ON buy_requests_recipients;
CREATE TRIGGER trg_buy_requests_recipients_updated_at
    BEFORE UPDATE ON buy_requests_recipients
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO purchase_statuses (code, name) VALUES ('PLANNED', 'Подготовка'), ('COLLECTING', 'В процессе сборки'), ('COMPLETED', 'Завершён') ON CONFLICT DO NOTHING;
INSERT INTO assigned_buy_request_statuses (code, name) VALUES ('ADDED', 'Добавлен в поход'), ('COLLECTING', 'Собирается'), ('COMPLETED', 'Завершён') ON CONFLICT DO NOTHING;
INSERT INTO buy_request_statuses (code, name) VALUES ('DRAFT', 'Черновик'), ('ADDED', 'Добавлен в поход'), ('COLLECTING', 'Собирается'), ('COMPLETED', 'Завершён'), ('AWAITING_DECISION', 'Ждёт решения заказчика') ON CONFLICT DO NOTHING;
INSERT INTO buy_item_statuses (code, name) VALUES ('BOUGHT', 'Куплено как просили'), ('QUANTITY_CHANGED', 'Куплено другое количество'), ('REPLACEMENT', 'Предложена замена'), ('NOT_FOUND', 'Товар не найден'), ('NOT_BOUGHT', 'Не куплен') ON CONFLICT DO NOTHING;
ALTER TABLE purchase ADD COLUMN IF NOT EXISTS receipt_data BYTEA;
ALTER TABLE purchase ADD COLUMN IF NOT EXISTS receipt_type VARCHAR(30);
COMMIT;
