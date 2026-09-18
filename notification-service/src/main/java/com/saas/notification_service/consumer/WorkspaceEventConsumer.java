package com.saas.notification_service.consumer;

import com.saas.notification_service.config.RabbitMQConfig;
import com.saas.notification_service.event.InvitationCreatedEvent;
import com.saas.notification_service.repository.ProcessedEventRepository;
import com.saas.notification_service.service.EmailService;
import com.saas.notification_service.service.InboxDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Sama seperti AuthEventConsumer: binding wildcard workspace.# bisa membawa banyak jenis
 * event, jadi payload diterima mentah lalu diarahkan berdasarkan jenisnya.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceEventConsumer {

    private static final String CONSUMER_NAME = "workspace-event-consumer";

    private final EmailService emailService;
    private final InboxDeliveryService inboxDeliveryService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_WORKSPACE_EVENTS)
    public void handle(Map<String, Object> payload,
                       @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {

        String eventType = asText(payload.get("eventType"));
        String discriminator = eventType != null ? eventType : routingKey;

        if (discriminator == null) {
            log.warn("Event workspace tanpa eventType maupun routing key, dilewati. keys={}", payload.keySet());
            return;
        }

        switch (discriminator) {
            case "INVITATION_CREATED", "workspace.invitation-created" ->
                    handleInvitationCreated(payload, discriminator);
            default ->
                    log.debug("Event workspace '{}' belum ditangani, dilewati.", discriminator);
        }
    }

    private void handleInvitationCreated(Map<String, Object> payload, String eventType) {
        InvitationCreatedEvent event;
        try {
            event = objectMapper.convertValue(payload, InvitationCreatedEvent.class);
        } catch (IllegalArgumentException ex) {
            // Payload yang bentuknya tidak cocok tidak akan pernah cocok berapa kali pun
            // diulang; kirim ke dead-letter sekarang daripada mengulang ~40 menit.
            log.error("Payload INVITATION_CREATED tidak bisa dibaca, dikirim ke dead-letter: {}", ex.getMessage());
            throw new AmqpRejectAndDontRequeueException("Payload tidak valid", ex);
        }

        if (event.getEmail() == null || event.getEmail().isBlank()) {
            log.error("Event undangan tanpa alamat tujuan, dikirim ke dead-letter.");
            throw new AmqpRejectAndDontRequeueException("Alamat email kosong");
        }

        String eventId = firstNonNull(
                asText(payload.get("eventId")),
                event.getInvitationId() != null ? event.getInvitationId().toString() : event.getToken());

        if (!processedEventRepository.tryClaim(eventId, CONSUMER_NAME, eventType)) {
            log.info("Undangan untuk {} sudah diklaim (concurrent run), dilewati.", event.getEmail());
            return;
        }

        String workspaceName = event.getWorkspaceName() != null ? event.getWorkspaceName() : "Workspace";
        String role = event.getRole() != null ? event.getRole() : "MEMBER";

        log.info("Menerima event INVITATION_CREATED untuk email: {} di workspace: {} oleh: {}",
                event.getEmail(), workspaceName,
                event.getInviterName() != null ? event.getInviterName() : "unknown");

        try {
            emailService.sendInvitationEmail(
                    event.getEmail(),
                    workspaceName,
                    role,
                    event.getAcceptUrl(),
                    event.getInviterName()
            );
            inboxDeliveryService.createInvitationNotification(
                    event.getEmail(),
                    event.getInviterName(),
                    workspaceName,
                    role,
                    event.getToken(),
                    event.getAcceptUrl(),
                    event.getExpiresAt()
            );
        } catch (RuntimeException ex) {
            // Klaim ditulis sebelum efek sampingnya; kalau gagal, lepaskan supaya
            // percobaan ulang tidak menyimpulkan "sudah diproses" dan membuangnya.
            processedEventRepository.releaseClaim(eventId, CONSUMER_NAME);
            throw ex;
        }
    }

    private static String asText(Object value) {
        return value != null ? value.toString() : null;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
