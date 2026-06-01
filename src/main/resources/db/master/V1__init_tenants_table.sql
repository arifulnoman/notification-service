CREATE TABLE IF NOT EXISTS tenants (
    id          UUID         NOT NULL PRIMARY KEY,
    tenant_id   VARCHAR(100) NOT NULL UNIQUE,
    db_url      VARCHAR(500) NOT NULL,
    db_username VARCHAR(255) NOT NULL,
    db_password VARCHAR(255) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tenants_tenant_id
    ON tenants (tenant_id);
