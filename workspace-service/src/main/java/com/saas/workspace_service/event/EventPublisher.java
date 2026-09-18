package com.saas.workspace_service.event;

import com.saas.workspace_service.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishInvitationCreated(InvitationCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_DOMAIN_EVENTS,
                    RabbitMQConfig.ROUTING_KEY_INVITATION_CREATED,
                    event
            );
            log.info("Published InvitationCreatedEvent for email: {}", event.getEmail());
        } catch (Exception ex) {
            log.error("Failed to publish InvitationCreatedEvent for email: {}", event.getEmail(), ex);
        }
    }
}
