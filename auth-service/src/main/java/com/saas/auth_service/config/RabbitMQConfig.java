package com.saas.auth_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_DOMAIN_EVENTS = "devflow.domain.events";
    public static final String ROUTING_KEY_EMAIL_VERIFICATION = "identity.email-verification-requested";

    @Bean
    public TopicExchange domainEventsExchange() {
        return new TopicExchange(EXCHANGE_DOMAIN_EVENTS, true, false);
    }

    // auth-service only publishes. notification-service owns notification.events
    // (with its dead-letter args) and its binding; re-declaring here without those
    // args triggers PRECONDITION_FAILED on every channel.

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
