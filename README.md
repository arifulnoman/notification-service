# Central Notification Service (CNS)

A high-performance, **multi-tenant real-time notification engine**. 
It ingests events from your backend microservices, dynamically provisions separate tenant databases on-the-fly, and pushes live updates to users via WebSockets.



## Architecture Flow

```mermaid
sequenceDiagram
    participant S as Source System (HRMS)
    participant MQ as RabbitMQ
    participant CNS as Notification Service
    participant DB as Postgres (Tenant DB)
    participant WS as Frontend (React/Angular)

    %% Publishing Flow
    S->>MQ: 1. Publish NotificationEventDTO
    MQ->>CNS: 2. Consume Message
    CNS->>CNS: 3. Extract tenantId
    CNS->>DB: 4. Auto-create DB & Tables (if new)
    CNS->>DB: 5. Save Notification
    
    %% Real-time Push
    CNS->>WS: 6. Push updated unreadCount & payload via WebSocket
    
    %% REST Fetching
    WS->>CNS: 7. GET /api/notifications (with JWT)
    CNS->>DB: 8. Fetch paginated list
    CNS-->>WS: 9. Return JSON
```

> [!TIP]
> **Zero Configuration:** If a notification arrives for a brand new `tenantId`, CNS automatically creates a new Postgres database and runs migrations in real-time. No manual setup required!
> 
> **Dead Letter Queue:** If a published message fails (e.g. missing `tenantId`), CNS retries 3 times and then safely parks it in the `notification.dlq` Dead Letter Queue for inspection. No data is lost.
