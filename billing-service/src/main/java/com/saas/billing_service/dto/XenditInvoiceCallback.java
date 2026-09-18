package com.saas.billing_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class XenditInvoiceCallback {
    private String id;

    @JsonProperty("external_id")
    private String externalId;

    private String status;
    private Long amount;

    @JsonProperty("paid_amount")
    private Long paidAmount;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("payment_channel")
    private String paymentChannel;

    @JsonProperty("paid_at")
    private Instant paidAt;
}
