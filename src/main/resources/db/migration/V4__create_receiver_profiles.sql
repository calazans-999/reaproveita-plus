CREATE TABLE receiver_profiles (
    organization_id BIGINT PRIMARY KEY REFERENCES organizations(id),
    capacity_kg NUMERIC(10, 2) NOT NULL,
    available_for_pickup BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_receiver_profiles_capacity CHECK (capacity_kg > 0)
);

CREATE TABLE receiver_profile_categories (
    organization_id BIGINT NOT NULL REFERENCES receiver_profiles(organization_id) ON DELETE CASCADE,
    category VARCHAR(30) NOT NULL,
    PRIMARY KEY (organization_id, category),
    CONSTRAINT ck_receiver_profile_categories CHECK (
        category IN ('FRUITS', 'VEGETABLES', 'BAKERY', 'GRAINS', 'OTHER')
    )
);
