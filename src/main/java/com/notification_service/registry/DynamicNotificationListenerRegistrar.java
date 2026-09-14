package com.notification_service.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification_service.dto.NotificationEventDTO;
import com.notification_service.service.NotificationConsumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Starts/stops a dedicated listener container per registered queue at
 * runtime, so a new integration's queue is consumed the moment it
 * registers — replacing the old static "@RabbitListener(queues=...)" list
 * that required editing config and redeploying for every new integration.
 *
 * Deserialization is done manually (rather than relying on a message
 * converter's type-inference) because every queue here always carries the
 * same {@link NotificationEventDTO} shape, so there is nothing to infer.
 */
@Slf4j
@Component
public class DynamicNotificationListenerRegistrar {

    private final ConnectionFactory connectionFactory;
    private final NotificationConsumer notificationConsumer;
    private final ObjectMapper objectMapper;

    /**
     * notificationConsumer is @Lazy because the registry (which owns this class)
     * is now also consulted by NotificationService for fresh sender info at read
     * time — without laziness here, that would form a startup-time bean cycle:
     * NotificationService -> SenderProfileResolver -> ServiceRegistryService ->
     * this -> NotificationConsumer -> NotificationService. Deferring resolution
     * is safe: this field is only ever used once a message actually arrives,
     * long after the context has finished starting.
     */
    public DynamicNotificationListenerRegistrar(
            ConnectionFactory connectionFactory,
            @Lazy NotificationConsumer notificationConsumer,
            ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.notificationConsumer = notificationConsumer;
        this.objectMapper = objectMapper;
    }

    @Value("${spring.rabbitmq.listener.simple.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${spring.rabbitmq.listener.simple.retry.initial-interval:1000}")
    private long retryInitialInterval;

    @Value("${spring.rabbitmq.listener.simple.retry.max-interval:10000}")
    private long retryMaxInterval;

    @Value("${spring.rabbitmq.listener.simple.retry.multiplier:2.0}")
    private double retryMultiplier;

    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();

    /**
     * Idempotent — calling this again for a queue that already has a running
     * container is a no-op, so both startup preload and a fresh registration
     * can call it safely.
     */
    public void registerListenerFor(String queueName) {
        containers.computeIfAbsent(queueName, this::startContainer);
    }

    public void stopListener(String queueName) {
        SimpleMessageListenerContainer container = containers.remove(queueName);
        if (container != null) {
            container.stop();
            log.info("Stopped dynamic listener container for queue [{}]", queueName);
        }
    }

    private SimpleMessageListenerContainer startContainer(String queueName) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener(message -> handleMessage(queueName, message));
        container.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(retryMaxAttempts)
                .backOffOptions(retryInitialInterval, retryMultiplier, retryMaxInterval)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        container.start();
        log.info("Started dynamic listener container for queue [{}]", queueName);
        return container;
    }

    private void handleMessage(String queueName, Message message) {
        NotificationEventDTO event;
        try {
            event = objectMapper.readValue(message.getBody(), NotificationEventDTO.class);
        } catch (Exception e) {
            log.error("Malformed notification event on queue [{}] — routing straight to DLQ, not retrying",
                    queueName, e);
            throw new AmqpRejectAndDontRequeueException("Malformed notification event payload", e);
        }
        notificationConsumer.consumeNotificationEvent(event);
    }
}
