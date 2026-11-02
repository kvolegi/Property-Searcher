-- =====================================================================
-- Property Searcher 3: Initial PostgreSQL Schema Migration
-- Standard: Flyway V1__init_schema.sql (Senior / Team Lead Enterprise Spec)
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(128),
    first_name VARCHAR(128),
    language_code VARCHAR(10) DEFAULT 'uk',
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS search_targets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    url TEXT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS discovered_properties (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(128) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    title VARCHAR(512) NOT NULL,
    price VARCHAR(64) NOT NULL,
    url TEXT NOT NULL,
    category VARCHAR(255),
    discovered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_external_platform UNIQUE (external_id, platform)
);

CREATE TABLE IF NOT EXISTS price_history (
    id BIGSERIAL PRIMARY KEY,
    property_id BIGINT NOT NULL REFERENCES discovered_properties(id) ON DELETE CASCADE,
    raw_price VARCHAR(64) NOT NULL,
    numeric_price NUMERIC(15, 2),
    currency VARCHAR(16),
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- Performance & Deduplication Indexes
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_properties_discovered_at ON discovered_properties(discovered_at DESC);
CREATE INDEX IF NOT EXISTS idx_properties_platform ON discovered_properties(platform);
CREATE INDEX IF NOT EXISTS idx_targets_active ON search_targets(active);
CREATE INDEX IF NOT EXISTS idx_price_history_property ON price_history(property_id, recorded_at DESC);
