CREATE TABLE IF NOT EXISTS notifications (
    id UUID NOT NULL PRIMARY KEY,
    source_system VARCHAR(255) NOT NULL,
    recipient_user_id VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    action_url VARCHAR(255),
    notification_type VARCHAR(50) NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_user_id 
    ON notifications (recipient_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_source_system 
    ON notifications (source_system);
