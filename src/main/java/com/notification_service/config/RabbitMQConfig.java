package com.notification_service.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queues}")
    private String queues;

    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Bean
    DirectExchange exchange() {
        return new DirectExchange(exchangeName);
    }

    /**
     * Dynamically declares all queues listed in rabbitmq.queues and binds each
     * to the shared exchange using the queue name as the routing key.
     * To add a new project queue, simply append its queue name to the
     * comma-separated list in application.properties.
     *
     * Example:
     *   rabbitmq.queues=hr.notification.queue,erp.notification.queue
     */
    @Bean
    Declarables notificationQueuesAndBindings(DirectExchange exchange) {
        List<String> queueNames = Arrays.stream(queues.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());

        List<Declarable> declarables = queueNames.stream()
                .flatMap(name -> {
                    Queue queue = new Queue(name, true);
                    Binding binding = BindingBuilder.bind(queue).to(exchange).with(name);
                    return java.util.stream.Stream.<Declarable>of(queue, binding);
                })
                .collect(Collectors.toList());

        return new Declarables(declarables);
    }

    @SuppressWarnings("removal")
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
