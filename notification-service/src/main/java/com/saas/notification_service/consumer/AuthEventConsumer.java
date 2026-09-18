package com.saas.notification_service.consumer;

import com.saas.notification_service.config.RabbitMQConfig;
import com.saas.notification_service.event.EmailVerificationRequestedEvent;
import com.saas.notification_service.repository.ProcessedEventRepository;
import com.saas.notification_service.service.EmailService;
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
 * Menerima payload mentah (Map) dari binding wildcard identity.#, bukan satu kelas event.
 * Mengetiknya ke satu kelas membuat SEMUA event identity dipaksa jadi kelas itu, field
 * tidak cocok jadi null, dan email terkirim berisi "null".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventConsumer {

    private static final String CONSUMER_NAME = "auth-event-consumer";

    private final EmailService emailService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATION_EVENTS)
    public void handle(Map<String, Object> payload,
                       @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {

        // Publisher belum mengirim field eventType, jadi routing key dipakai sebagai
        // penentu. Keduanya didukung supaya publisher bebas menambah eventType nanti.
        String eventType = asText(payload.get("eventType"));
        String discriminator = eventType != null ? eventType : routingKey;

        if (discriminator == null) {
            log.warn("Event identity tanpa eventType maupun routing key, dilewati. keys={}", payload.keySet());
            return;
        }

        switch (discriminator) {
            case "EMAIL_VERIFICATION_REQUESTED", "identity.email-verification-requested" ->
                    handleEmailVerificationRequested(payload, discriminator);
            default ->
                    log.debug("Event identity '{}' belum ditangani, dilewati.", discriminator);
        }
    }

    private void handleEmailVerificationRequested(Map<String, Object> payload, String eventType) {
        EmailVerificationRequestedEvent event;
        try {
            event = objectMapper.convertValue(payload, EmailVerificationRequestedEvent.class);
        } catch (IllegalArgumentException ex) {
            // Payload yang bentuknya tidak cocok tidak akan pernah cocok berapa kali pun
            // diulang. Tanpa ini pesannya diulang di dalam proses selama ~40 menit lalu
            // kembali ke antrian. AmqpRejectAndDontRequeue mengirimnya ke dead-letter
            // exchange sekarang juga, di mana ia bisa diperiksa.
            log.error("Payload EMAIL_VERIFICATION_REQUESTED tidak bisa dibaca, dikirim ke dead-letter: {}",
                    ex.getMessage());
            throw new AmqpRejectAndDontRequeueException("Payload tidak valid", ex);
        }

        if (event.getEmail() == null || event.getEmail().isBlank()) {
            log.error("Event verifikasi email tanpa alamat tujuan, dikirim ke dead-letter.");
            throw new AmqpRejectAndDontRequeueException("Alamat email kosong");
        }

        // verificationToken unik per permintaan (resend menghasilkan token baru), jadi aman
        // dipakai saat publisher belum mengirim eventId.
        String eventId = firstNonNull(asText(payload.get("eventId")), event.getVerificationToken());

        if (!processedEventRepository.tryClaim(eventId, CONSUMER_NAME, eventType)) {
            log.info("Event verifikasi email untuk {} sudah diklaim (concurrent run), dilewati.", event.getEmail());
            return;
        }

        log.info("Menerima event EMAIL_VERIFICATION_REQUESTED untuk user: {} ({})",
                event.getFullName(), event.getEmail());

        try {
            emailService.sendVerificationEmail(
                    event.getEmail(),
                    event.getFullName(),
                    event.getVerificationUrl()
            );
        } catch (RuntimeException ex) {
            // Klaim ditulis sebelum efek sampingnya, jadi kalau efek itu gagal klaimnya
            // harus dilepas. Tanpa ini percobaan ulang melihat klaim lama, menyimpulkan
            // "sudah diproses", dan membuang emailnya diam-diam.
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
