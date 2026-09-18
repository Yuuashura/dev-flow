package com.saas.billing_service.controller;

import com.saas.billing_service.dto.XenditInvoiceCallback;
import com.saas.billing_service.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/xendit")
@RequiredArgsConstructor
public class XenditWebhookController {

    private final BillingService billingService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> xenditWebhook(
            @RequestHeader(value = "x-callback-token", required = false) String callbackToken,
            @RequestBody XenditInvoiceCallback callback) {
        billingService.processXenditCallback(callbackToken, callback);
        return ResponseEntity.ok().build();
    }
}
