package com.saas.notification_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_DOMAIN_EVENTS = "devflow.domain.events";
    public static final String QUEUE_NOTIFICATION_EVENTS = "notification.events";

    public static final String EXCHANGE_DEAD_LETTER = "dead-letter.events";
    public static final String QUEUE_DEAD_LETTER = "dead-letter.queue";
    public static final String ROUTING_KEY_DEAD_LETTER = "dlq";

    @Bean
    public TopicExchange domainEventsExchange() {
        return new TopicExchange(EXCHANGE_DOMAIN_EVENTS, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(EXCHANGE_DEAD_LETTER, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DEAD_LETTER).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY_DEAD_LETTER);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_EVENTS)
                .deadLetterExchange(EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    public static final String QUEUE_WORKSPACE_EVENTS = "notification.workspace.events";

    @Bean
    public Queue workspaceQueue() {
        return QueueBuilder.durable(QUEUE_WORKSPACE_EVENTS)
                .deadLetterExchange(EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Binding notificationIdentityBinding(Queue notificationQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(domainEventsExchange).with("identity.#");
    }

    @Bean
    public Binding notificationWorkspaceBinding(Queue workspaceQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(workspaceQueue).to(domainEventsExchange).with("workspace.#");
    }

    public static final String QUEUE_BILLING_EVENTS = "notification.billing.events";

    @Bean
    public Queue billingQueue() {
        return QueueBuilder.durable(QUEUE_BILLING_EVENTS)
                .deadLetterExchange(EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Binding notificationBillingBinding(Queue billingQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(billingQueue).to(domainEventsExchange).with("billing.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
