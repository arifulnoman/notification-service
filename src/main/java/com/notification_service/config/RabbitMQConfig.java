package com.notification_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared RabbitMQ infra: the exchange every registered service binds to, and
 * the dead-letter queue every registered service's queue routes into after
 * retries are exhausted.
 *
 * Per-service queues are no longer declared here — they are provisioned at
 * runtime by {@code ServiceRegistryService} as each service self-registers
 * via POST /api/admin/services, so onboarding a new integration never
 * requires editing this config or redeploying.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.queue.dead-letter}")
    private String deadLetterQueueName;

    @Bean
    DirectExchange exchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    Queue deadLetterQueue() {
        return new Queue(deadLetterQueueName, true);
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(exchange()).with(deadLetterQueueName);
    }

    @SuppressWarnings("removal")
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
