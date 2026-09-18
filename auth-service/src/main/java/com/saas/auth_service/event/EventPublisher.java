package com.saas.auth_service.event;

import com.saas.auth_service.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_DOMAIN_EVENTS,
                    RabbitMQConfig.ROUTING_KEY_EMAIL_VERIFICATION,
                    event
            );
            log.info("Published EmailVerificationRequestedEvent for email: {}", event.getEmail());
        } catch (Exception ex) {
            log.error("Failed to publish EmailVerificationRequestedEvent for email: {}", event.getEmail(), ex);
        }
    }
}
