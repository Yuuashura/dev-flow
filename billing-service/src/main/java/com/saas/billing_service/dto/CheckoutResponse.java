package com.saas.billing_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckoutResponse {
    private String externalId;
    private String status;
    private String invoiceUrl;
}
