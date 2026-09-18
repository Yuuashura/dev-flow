package com.saas.billing_service.config;

import com.saas.billing_service.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class PaymentReconciliationJob {

    private final BillingService billingService;

    @PostConstruct
    public void reconcileOnStartup() {
        billingService.reconcilePendingPayments();
    }

    @Scheduled(fixedDelayString = "${xendit.reconciliation-interval-ms:60000}")
    public void reconcilePendingPayments() {
        billingService.reconcilePendingPayments();
    }
}
