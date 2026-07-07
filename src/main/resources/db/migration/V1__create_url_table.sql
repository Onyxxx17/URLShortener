CREATE TABLE IF NOT EXISTS url (
    id              BIGSERIAL       PRIMARY KEY,
    original_url    VARCHAR(2048)   NOT NULL,
    short_code      VARCHAR(10)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    click_count     BIGINT          NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,

    CONSTRAINT uq_short_code UNIQUE (short_code)
);

-- Hot-path lookup: every redirect hits this
CREATE INDEX IF NOT EXISTS idx_short_code ON url (short_code);

-- Duplicate detection on create
CREATE INDEX IF NOT EXISTS idx_original_url ON url (original_url);

-- Cleanup scheduler: find expired URLs efficiently
CREATE INDEX IF NOT EXISTS idx_expires_at ON url (expires_at) WHERE expires_at IS NOT NULL;
