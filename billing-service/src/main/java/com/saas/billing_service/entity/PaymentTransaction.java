package com.saas.billing_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "owner_payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "IDR";

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(length = 50)
    @Builder.Default
    private String provider = "XENDIT";

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @Column(name = "invoice_url", length = 1000)
    private String invoiceUrl;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "payment_channel")
    private String paymentChannel;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
