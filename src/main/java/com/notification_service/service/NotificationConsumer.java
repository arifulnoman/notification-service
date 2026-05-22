package com.notification_service.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.notification_service.dto.NotificationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void consumeNotificationEvent(NotificationEvent event) {
        log.info("Received notification event from source: {} for user: {}", event.getSourceSystem(), event.getRecipientUserId());
        try {
            notificationService.processNotificationEvent(event);
        } catch (Exception e) {
            log.error("Failed to process notification event", e);
            // In a production environment, you would want to implement a dead-letter queue (DLQ)
            // or retry mechanism here depending on the error type.
        }
    }
}
