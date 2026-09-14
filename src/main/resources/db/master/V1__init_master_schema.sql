-- Master DB schema: tenant registry + service registry.
-- Not tenant-scoped itself — this is the single central database CNS uses
-- to know which tenant DBs and which publishing services exist.

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

-- Self-service registry of systems that publish notifications to CNS
-- (ERP, HRMS, or any future integration). Registering a row here is what
-- provisions its RabbitMQ queue and starts consuming it — no static
-- "rabbitmq.queues" property, no redeploy needed for a new integration.
--
-- Keyed by (tenant_id, source_system), not source_system alone: each
-- tenant runs its own separate instance of a given source system, with
-- its own JWT signing key and its own user directory — so "erp" for
-- tenant A and "erp" for tenant B are two independent registrations.
CREATE TABLE IF NOT EXISTS registered_services (
    id                UUID         NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(100) NOT NULL,
    source_system     VARCHAR(100) NOT NULL,
    jwt_public_key    TEXT,
    queue_name        VARCHAR(255) NOT NULL,
    user_lookup_url   VARCHAR(500),
    user_lookup_api_key VARCHAR(500),
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uq_registered_services_tenant_source UNIQUE (tenant_id, source_system),
    CONSTRAINT uq_registered_services_queue_name UNIQUE (queue_name)
);

CREATE INDEX IF NOT EXISTS idx_registered_services_active
    ON registered_services (is_active);

-- No seed rows: every integration (existing or new) onboards the same way —
-- POST /api/admin/services when it starts using CNS.
