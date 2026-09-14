package com.notification_service.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.notification_service.dto.NotificationEventDTO;
import com.notification_service.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    /**
     * Invoked by a dynamically started listener container — one per registered
     * service's queue, wired up by {@code DynamicNotificationListenerRegistrar}
     * (see {@code com.notification_service.registry}) instead of a static
     * "@RabbitListener(queues=...)" list, so a new integration's queue starts
     * being consumed the moment it registers.
     *
     * MQ listener threads have no HTTP context, so the JWT filter never runs.
     * TenantContext is set manually from the event's tenantId field so that
     * TenantAwareDataSource routes JPA queries to the correct tenant DB.
     */
    public void consumeNotificationEvent(NotificationEventDTO event) {
        if (!StringUtils.hasText(event.getTenantId())) {
            log.error("Received notification event without a tenantId from source: [{}]. Rejecting message.", 
                    event.getSourceSystem());
            throw new IllegalArgumentException("tenantId is missing from notification event");
        }

        log.info("Received notification event from source: [{}], tenant: [{}], users: [{}]",
                event.getSourceSystem(), event.getTenantId(), event.getRecipientUserIds());
        try {
            TenantContext.setTenantId(event.getTenantId());
            notificationService.processNotificationEvent(event);
        } catch (Exception e) {
            log.error("Failed to process notification event from source: [{}], tenant: [{}]",
                    event.getSourceSystem(), event.getTenantId(), e);
            throw e;
        } finally {
            TenantContext.clear();
        }
    }
}
