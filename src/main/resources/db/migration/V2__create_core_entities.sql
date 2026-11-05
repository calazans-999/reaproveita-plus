CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'DONOR', 'RECEIVER'))
);

CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES app_users(id),
    name VARCHAR(160) NOT NULL,
    type VARCHAR(20) NOT NULL,
    city VARCHAR(120) NOT NULL,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_organizations_type CHECK (type IN ('DONOR', 'RECEIVER')),
    CONSTRAINT ck_organizations_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_organizations_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE TABLE donations (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    title VARCHAR(160) NOT NULL,
    category VARCHAR(30) NOT NULL,
    quantity_kg NUMERIC(10, 2) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    storage_condition VARCHAR(255) NOT NULL,
    sanitary_declaration BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_donations_quantity CHECK (quantity_kg > 0),
    CONSTRAINT ck_donations_category CHECK (category IN ('FRUITS', 'VEGETABLES', 'BAKERY', 'GRAINS', 'OTHER')),
    CONSTRAINT ck_donations_status CHECK (status IN ('AVAILABLE', 'RESERVED', 'COLLECTED', 'CANCELLED', 'EXPIRED', 'BLOCKED'))
);

CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    donation_id BIGINT NOT NULL REFERENCES donations(id),
    receiver_organization_id BIGINT NOT NULL REFERENCES organizations(id),
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    collected_at TIMESTAMPTZ,
    CONSTRAINT ck_reservations_status CHECK (status IN ('REQUESTED', 'CONFIRMED', 'COLLECTED', 'CANCELLED'))
);

CREATE INDEX idx_organizations_owner ON organizations(owner_id);
CREATE INDEX idx_donations_organization ON donations(organization_id);
CREATE INDEX idx_donations_status_expiry ON donations(status, expires_at);
CREATE INDEX idx_reservations_receiver ON reservations(receiver_organization_id);
CREATE INDEX idx_reservations_donation ON reservations(donation_id);
CREATE UNIQUE INDEX uq_reservation_active_per_donation
    ON reservations(donation_id)
    WHERE status IN ('REQUESTED', 'CONFIRMED', 'COLLECTED');