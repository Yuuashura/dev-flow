package com.saas.billing_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PaymentStatusResponse {
    private String externalId;
    private String status;
    private long amount;
    private String invoiceUrl;
    private Instant paidAt;
}
