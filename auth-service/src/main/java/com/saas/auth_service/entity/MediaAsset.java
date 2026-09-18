package com.saas.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaKind kind;

    /** Ditentukan server dari byte hasil encode ulang — bukan dari header klien. */
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    /** Dipakai sebagai ETag: gambar tidak berubah tidak perlu dikirim ulang. */
    @Column(nullable = false, length = 64)
    private String sha256;

    /** Tanpa @Lob: di Postgres, Hibernate memetakan @Lob byte[] ke `oid` (large
     *  object), bukan `bytea`, lalu mencoba mengubah tipe kolom saat start dan gagal —
     *  bytea tidak bisa di-cast otomatis ke oid. byte[] polos memetakan langsung ke
     *  bytea, yang memang tipe kolomnya di V12. */
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
