package com.notification_service.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.notification_service.dto.NotificationEventDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    /**
     * Listens to all queues defined in rabbitmq.queues (comma-separated).
     * 
     * Example property:
     *   rabbitmq.queues=hr.notification.queue,erp.notification.queue
     */
    @RabbitListener(queues = "#{'${rabbitmq.queues}'.split(',')}")
    public void consumeNotificationEvent(NotificationEventDTO event) {
        log.info("Received notification event from source: [{}] for users: [{}]",
                event.getSourceSystem(), event.getRecipientUserIds());
        try {
            notificationService.processNotificationEvent(event);
        } catch (Exception e) {
            log.error("Failed to process notification event from source: [{}]",
                    event.getSourceSystem(), e);
            // TODO: route to Dead Letter Queue (DLQ) for unrecoverable errors
        }
    }
}
