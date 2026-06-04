-- Tenant-scoped notifications table.
-- This migration runs against each tenant's own database.
-- No tenant_id column needed here — isolation is enforced at the DataSource level.
CREATE TABLE IF NOT EXISTS notifications (
    id                UUID         NOT NULL PRIMARY KEY,
    source_system     VARCHAR(255) NOT NULL,
    recipient_user_id VARCHAR(255) NOT NULL,
    message           TEXT         NOT NULL,
    action_url        VARCHAR(255),
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

-- Primary lookup: list notifications for a user, ordered by newest first
CREATE INDEX IF NOT EXISTS idx_notifications_user
    ON notifications (recipient_user_id, created_at DESC);

-- Unread count query
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
    ON notifications (recipient_user_id, is_read);

CREATE INDEX IF NOT EXISTS idx_notifications_source
    ON notifications (source_system);
