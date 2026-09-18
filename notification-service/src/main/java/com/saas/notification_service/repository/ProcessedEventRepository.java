package com.saas.notification_service.repository;

import com.saas.notification_service.entity.ProcessedEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);

    void deleteByEventIdAndConsumerName(String eventId, String consumerName);

    /** Coba sisipkan sebelum memproses efek samping (kirim email, push notifikasi).
     *  Kembalikan true bila baris baru disisipkan — hanya pemenang yang memproses.
     *  UNIQUE constraint di eventId menjamin tepat satu pemenang dalam concurrent run.
     *
     *  <p>REQUIRES_NEW: klaimnya harus benar-benar ter-commit sebelum efek samping
     *  dijalankan, kalau tidak dua consumer bersamaan sama-sama lolos.
     *  {@link #releaseClaim} wajib dipanggil bila efek sampingnya gagal. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    default boolean tryClaim(String eventId, String consumerName, String eventType) {
        if (eventId == null) return true; // Tidak bisa dedup tanpa ID: proses seperti biasa.
        try {
            save(ProcessedEvent.builder()
                    .id(UUID.randomUUID())
                    .eventId(fitKey(eventId))
                    .consumerName(truncate(consumerName, 100))
                    .eventType(truncate(eventType, 100))
                    .processedAt(Instant.now())
                    .build());
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    /**
     * Lepaskan klaim supaya event yang gagal bisa diulang.
     *
     * <p>Klaim sengaja ditulis sebelum efek sampingnya, jadi kalau efek itu gagal
     * klaimnya harus dibatalkan. Tanpa ini, pengiriman email yang gagal karena SMTP
     * sedang mati akan membuat percobaan ulang berikutnya melihat klaim yang sudah
     * ada, menyimpulkan "sudah diproses", dan membuang pesannya diam-diam —
     * kegagalan yang tidak muncul di mana pun.
     *
     * <p>REQUIRES_NEW supaya penghapusan ini tetap ter-commit walau transaksi
     * pemanggilnya sedang di-rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    default void releaseClaim(String eventId, String consumerName) {
        if (eventId == null) return;
        deleteByEventIdAndConsumerName(fitKey(eventId), truncate(consumerName, 100));
    }

    /**
     * Kunci idempotency yang muat di kolom varchar(255) tanpa kehilangan keunikan.
     *
     * <p>Pemotongan lugas dulu memotong kunci panjang ke 255 karakter, jadi dua event
     * berbeda yang berbagi 255 karakter pertama bertabrakan di UNIQUE index dan yang
     * kedua dianggap duplikat — notifikasinya hilang. Kunci yang kepanjangan sekarang
     * diganti hash-nya, yang tetap deterministik dan selalu muat.
     */
    private static String fitKey(String eventId) {
        if (eventId.length() <= 255) return eventId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(eventId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 wajib ada di setiap JVM; kalau tidak, pemotongan lama lebih baik
            // daripada gagal total.
            return eventId.substring(0, 255);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
