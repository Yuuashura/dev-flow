package com.saas.billing_service.controller;

import com.saas.billing_service.dto.PlanLimits;
import com.saas.billing_service.dto.CheckoutRequest;
import com.saas.billing_service.dto.CheckoutResponse;
import com.saas.billing_service.dto.OrderHistoryResponse;
import com.saas.billing_service.dto.PaymentStatusResponse;
import com.saas.billing_service.dto.XenditInvoiceCallback;
import com.saas.billing_service.entity.OwnerPlan;
import com.saas.billing_service.entity.Plan;
import com.saas.billing_service.entity.Subscription;
import com.saas.billing_service.service.BillingService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/plans")
    public ResponseEntity<List<Plan>> getPlans() {
        return ResponseEntity.ok(billingService.getAllPlans());
    }

    @GetMapping("/plans/{code}")
    public ResponseEntity<Plan> getPlanByCode(@PathVariable String code) {
        Plan plan = billingService.getPlanByCode(code);
        return plan != null ? ResponseEntity.ok(plan) : ResponseEntity.notFound().build();
    }

    @GetMapping("/workspaces/{workspaceId}/subscription")
    public ResponseEntity<Subscription> getSubscription(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workspaceId) {
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(billingService.getWorkspaceSubscription(userId, workspaceId));
    }

    @GetMapping("/owner/plan")
    public ResponseEntity<OwnerPlan> getMyPlan(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(billingService.getOwnerPlan(userId));
    }

    @GetMapping("/owner/plan/limits")
    public ResponseEntity<PlanLimits> getMyPlanLimits(@AuthenticationPrincipal UUID userId) {
        PlanLimits limits = billingService.getPlanLimitsByOwner(userId);
        return limits != null ? ResponseEntity.ok(limits) : ResponseEntity.notFound().build();
    }

    @PostMapping("/owner/upgrade")
    @Deprecated
    public ResponseEntity<OwnerPlan> upgrade(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(410).build();
    }

    @PostMapping("/owner/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(billingService.createProCheckout(userId, request));
    }

    @GetMapping("/payments")
    public ResponseEntity<org.springframework.data.domain.Page<OrderHistoryResponse>> getOrderHistory(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(billingService.getOrderHistory(userId, page, size, status));
    }

    @GetMapping("/payments/{externalId}")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String externalId) {
        return ResponseEntity.ok(billingService.getPaymentStatus(userId, externalId));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> xenditWebhook(
            @RequestHeader(value = "x-callback-token", required = false) String callbackToken,
            @RequestBody XenditInvoiceCallback callback) {
        billingService.processXenditCallback(callbackToken, callback);
        return ResponseEntity.ok().build();
    }

}
