package com.saas.billing_service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class OrderHistoryResponse {

    private UUID id;
    private String externalId;
    private String planName;
    private String description;
    private long amount;
    private String currency;
    private String status;
    private String paymentMethod;
    private String paymentChannel;
    private Instant paidAt;
    private Instant createdAt;
    private String invoiceUrl;
}
