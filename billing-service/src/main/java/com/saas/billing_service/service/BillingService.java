package com.saas.billing_service.service;

import com.saas.billing_service.dto.CheckoutResponse;
import com.saas.billing_service.dto.CheckoutRequest;
import com.saas.billing_service.dto.OrderHistoryResponse;
import com.saas.billing_service.dto.PaymentStatusResponse;
import com.saas.billing_service.dto.PlanLimits;
import com.saas.billing_service.dto.XenditInvoiceCallback;
import com.saas.billing_service.entity.*;
import com.saas.billing_service.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OwnerPlanRepository ownerPlanRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${xendit.base-url:https://api.xendit.co}")
    private String xenditBaseUrl;

    @Value("${xendit.api-key}")
    private String xenditApiKey;

    @Value("${xendit.callback-token}")
    private String xenditCallbackToken;

    @Value("${xendit.callback-url:}")
    private String xenditCustomCallbackUrl;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.auth-service-url:http://localhost:8081}")
    private String authServiceUrl;

    /** Shared secret untuk /notifications/internal. Tanpa default: startup gagal kalau belum di-set. */
    @Value("${app.internal-token}")
    private String internalToken;

    private void sendBillingInboxNotification(UUID userId, String title, String content,
                                               String priority, String actionUrl) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("recipientUserId", userId.toString());
            req.put("type", "BILLING");
            req.put("title", title);
            req.put("content", content);
            req.put("priority", priority);
            if (actionUrl != null) req.put("actionUrl", actionUrl);
            RestClient.create(authServiceUrl)
                    .post()
                    .uri("/api/v1/notifications/internal")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Gagal mengirim billing notifikasi inbox untuk userId {}: {}", userId, ex.getMessage());
        }
    }

    @PostConstruct
    public void seedPlans() {
        seedPlan("FREE", "Free Plan", "Untuk tim kecil yang baru memulai. Terbatas 3 workspace dan 3 proyek per workspace.", 0, 3, 3, 5, 1, 1, 3);
        seedPlan("PRO", "Premium Plan", "Semua tanpa batas: workspace, proyek, anggota, GitHub, dan milestone.", 50000, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 20, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private void seedPlan(String code, String name, String desc, long price,
                          int maxWorkspaces, int maxProjects, int maxMembers, int maxStorage,
                          int githubRepos, int milestones) {
        planRepository.findByCode(code).ifPresentOrElse(p -> {
            p.setName(name);
            p.setDescription(desc);
            p.setPriceAmount(price);
            p.setMaxWorkspaces(maxWorkspaces);
            p.setMaxProjectsPerWorkspace(maxProjects);
            p.setMaxMembersPerWorkspace(maxMembers);
            p.setMaxStorageGb(maxStorage);
            p.setGithubRepos(githubRepos);
            p.setMilestonesPerProject(milestones);
            p.setActive(true);
            planRepository.save(p);
        }, () -> planRepository.save(Plan.builder()
                .code(code)
                .name(name)
                .description(desc)
                .priceAmount(price)
                .billingInterval("MONTHLY")
                .maxWorkspaces(maxWorkspaces)
                .maxProjectsPerWorkspace(maxProjects)
                .maxMembersPerWorkspace(maxMembers)
                .maxStorageGb(maxStorage)
                .githubRepos(githubRepos)
                .milestonesPerProject(milestones)
                .active(true)
                .build()));
    }

    public List<Plan> getAllPlans() {
        return planRepository.findAll();
    }

    public Plan getPlanByCode(String code) {
        return planRepository.findByCode(code).orElse(null);
    }

    public PlanLimits getPlanLimitsByOwner(UUID userId) {
        String planCode = getOwnerPlan(userId).getPlanCode();
        Plan plan = getPlanByCode(planCode);
        if (plan == null) {
            return null;
        }
        return PlanLimits.builder()
                .planCode(plan.getCode())
                .maxWorkspaces(plan.getMaxWorkspaces() != null ? plan.getMaxWorkspaces() : Integer.MAX_VALUE)
                .maxProjectsPerWorkspace(plan.getMaxProjectsPerWorkspace() != null ? plan.getMaxProjectsPerWorkspace() : Integer.MAX_VALUE)
                .maxMembersPerWorkspace(plan.getMaxMembersPerWorkspace() != null ? plan.getMaxMembersPerWorkspace() : Integer.MAX_VALUE)
                .maxStorageGb(plan.getMaxStorageGb() != null ? plan.getMaxStorageGb() : 20)
                .githubRepos(plan.getGithubRepos() != null ? plan.getGithubRepos() : Integer.MAX_VALUE)
                .milestonesPerProject(plan.getMilestonesPerProject() != null ? plan.getMilestonesPerProject() : Integer.MAX_VALUE)
                .build();
    }

    @Transactional
    public OwnerPlan getOwnerPlan(UUID userId) {
        OwnerPlan ownerPlan = ownerPlanRepository.findByUserId(userId).orElseGet(() -> {
            // find-then-insert tanpa sinkronisasi terhadap UNIQUE(user_id): dua request
            // pertama yang bersamaan sama-sama melihat baris kosong dan sama-sama
            // INSERT. ON CONFLICT DO NOTHING menyerahkan penentuannya ke Postgres,
            // lalu barisnya dibaca ulang — siapa pun yang menang, keduanya dapat
            // baris yang sama.
            jdbcTemplate.update(
                    "INSERT INTO owner_plans (id, user_id, plan_code, active, plan_start, created_at, updated_at) "
                            + "VALUES (gen_random_uuid(), ?, 'FREE', true, now(), now(), now()) "
                            + "ON CONFLICT (user_id) DO NOTHING",
                    userId);
            return ownerPlanRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Gagal menyiapkan data paket."));
        });
        if ("PRO".equals(ownerPlan.getPlanCode()) && ownerPlan.getPlanEnd() != null
                && ownerPlan.getPlanEnd().isBefore(Instant.now())) {
            ownerPlan.setPlanCode("FREE");
            ownerPlan.setPlanStart(Instant.now());
            ownerPlan.setPlanEnd(null);
            return ownerPlanRepository.save(ownerPlan);
        }
        return ownerPlan;
    }

    public boolean isPremium(UUID userId) {
        OwnerPlan ownerPlan = getOwnerPlan(userId);
        return "PRO".equals(ownerPlan.getPlanCode()) && ownerPlan.isActive();
    }

    @Transactional
    public CheckoutResponse createProCheckout(UUID userId, CheckoutRequest request) {
        if (isPremium(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paket Premium sudah aktif");
        }

        Plan proPlan = planRepository.findByCode("PRO")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paket Premium tidak ditemukan"));
        String externalId = "devflow-pro-" + userId + "-" + UUID.randomUUID();
        String billingUrl = frontendUrl + "/w/" + request.getWorkspaceSlug() + "/billing";

        Map<String, Object> customer = new java.util.HashMap<>();
        customer.put("given_names", request.getCustomerName());
        customer.put("email", request.getCustomerEmail());
        if (request.getCustomerPhone() != null && !request.getCustomerPhone().isBlank()) {
            customer.put("mobile_number", request.getCustomerPhone());
        }

        Map<String, Object> invoiceRequest = new java.util.HashMap<>();
        invoiceRequest.put("external_id", externalId);
        invoiceRequest.put("amount", proPlan.getPriceAmount());
        invoiceRequest.put("description", "DevFlow Premium - 30 hari");
        invoiceRequest.put("invoice_duration", 86400);
        invoiceRequest.put("success_redirect_url", billingUrl + "?payment=success&external_id=" + externalId);
        invoiceRequest.put("failure_redirect_url", billingUrl + "?payment=failed&external_id=" + externalId);
        String webhookUrl = (xenditCustomCallbackUrl != null && !xenditCustomCallbackUrl.isBlank())
                ? xenditCustomCallbackUrl
                : (frontendUrl + "/api/xendit/webhook");
        invoiceRequest.put("callback_url", webhookUrl);
        invoiceRequest.put("currency", proPlan.getCurrency());
        invoiceRequest.put("customer", customer);
        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            invoiceRequest.put("items", List.of(Map.of(
                    "name", "DevFlow Premium - " + request.getCompanyName(),
                    "price", proPlan.getPriceAmount(),
                    "quantity", 1
            )));
        }

        Map<?, ?> invoice;
        try {
            invoice = RestClient.builder()
                    .baseUrl(xenditBaseUrl)
                    .defaultHeaders(headers -> headers.setBasicAuth(xenditApiKey, ""))
                    .build()
                    .post()
                    .uri("/v2/invoices")
                    .body(invoiceRequest)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            log.error("Failed to create Xendit invoice for user {}", userId, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gagal membuat invoice Xendit");
        }

        if (invoice == null || invoice.get("invoice_url") == null || invoice.get("id") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respons invoice Xendit tidak valid");
        }

        PaymentTransaction transaction = PaymentTransaction.builder()
                .userId(userId)
                .externalId(externalId)
                .description("DevFlow Premium - 30 hari")
                .amount(proPlan.getPriceAmount())
                .currency(proPlan.getCurrency())
                .status("PENDING")
                .provider("XENDIT")
                .providerTransactionId(invoice.get("id").toString())
                .invoiceUrl(invoice.get("invoice_url").toString())
                .build();
        paymentTransactionRepository.save(transaction);

        return CheckoutResponse.builder()
                .externalId(externalId)
                .status(transaction.getStatus())
                .invoiceUrl(transaction.getInvoiceUrl())
                .build();
    }

    @Transactional
    public Page<OrderHistoryResponse> getOrderHistory(UUID userId, int page, int size, String status) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentTransaction> transactions;
        if (status != null && !status.isBlank()) {
            transactions = paymentTransactionRepository.findByUserIdAndStatus(userId, status.toUpperCase(), pageable);
        } else {
            transactions = paymentTransactionRepository.findByUserId(userId, pageable);
        }
        return transactions.map(transaction -> OrderHistoryResponse.builder()
                .id(transaction.getId())
                .externalId(transaction.getExternalId())
                .planName("DevFlow Premium")
                .description(transaction.getDescription() != null ? transaction.getDescription() : "DevFlow Premium - 30 hari")
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .paymentMethod(transaction.getPaymentMethod())
                .paymentChannel(transaction.getPaymentChannel())
                .paidAt(transaction.getPaidAt())
                .createdAt(transaction.getCreatedAt())
                .invoiceUrl(transaction.getInvoiceUrl())
                .build());
    }

    @Transactional
    public PaymentStatusResponse getPaymentStatus(UUID userId, String externalId) {
        // Kepemilikan dicek lewat pembacaan tanpa lock — murah, dan hanya menentukan
        // boleh-tidaknya melihat.
        paymentTransactionRepository.findByExternalIdAndUserId(externalId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaksi tidak ditemukan"));

        // Mutasinya harus lewat baris yang sama yang dikunci webhook. Sebelumnya
        // jalur ini memakai pembacaan tanpa lock lalu tetap mengubah state, jadi polling
        // frontend dan callback Xendit bisa sama-sama membaca PENDING dan sama-sama
        // mengaktifkan premium.
        PaymentTransaction transaction = paymentTransactionRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaksi tidak ditemukan"));
        if ("PENDING".equals(transaction.getStatus())) {
            refreshPaymentFromXendit(transaction);
        }
        return PaymentStatusResponse.builder()
                .externalId(transaction.getExternalId())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .invoiceUrl(transaction.getInvoiceUrl())
                .paidAt(transaction.getPaidAt())
                .build();
    }

    @Transactional
    public void processXenditCallback(String callbackToken, XenditInvoiceCallback callback) {
        // Rahasia kosong tidak boleh dipakai membandingkan apa pun: MessageDigest.isEqual
        // atas dua array kosong bernilai true, jadi konfigurasi yang kosong berubah
        // menjadi "header x-callback-token kosong lolos" — webhook pembayaran terbuka
        // untuk siapa saja. Tolak sebelum membandingkan.
        if (xenditCallbackToken == null || xenditCallbackToken.isBlank()) {
            log.error("XENDIT_CALLBACK_TOKEN belum diset; callback Xendit ditolak.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Verifikasi callback pembayaran belum dikonfigurasi.");
        }
        // Constant-time compare: String.equals short-circuits on the first
        // differing byte, which leaks the token prefix to a timing attacker.
        if (callbackToken == null || !MessageDigest.isEqual(
                callbackToken.getBytes(StandardCharsets.UTF_8),
                xenditCallbackToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Callback token Xendit tidak valid");
        }
        if (callback.getExternalId() == null || callback.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload callback Xendit tidak lengkap");
        }

        // findByExternalId holds a PESSIMISTIC_WRITE lock: the reconciliation job
        // polls the same PENDING rows every 60s. Without it both paths can read
        // PENDING, both pass the paid-amount check, and premium gets activated
        // (and the inbox notified) twice.
        PaymentTransaction transaction = paymentTransactionRepository.findByExternalId(callback.getExternalId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaksi tidak ditemukan"));
        applyInvoiceStatus(transaction, callback);
    }

    private void refreshPaymentFromXendit(PaymentTransaction transaction) {
        XenditInvoiceCallback invoice;
        try {
            invoice = RestClient.builder()
                    .baseUrl(xenditBaseUrl)
                    .defaultHeaders(headers -> headers.setBasicAuth(xenditApiKey, ""))
                    .build()
                    .get()
                    .uri("/v2/invoices/{id}", transaction.getProviderTransactionId())
                    .retrieve()
                    .body(XenditInvoiceCallback.class);
        } catch (Exception ex) {
            log.warn("Failed to fetch Xendit invoice {}: {}", transaction.getExternalId(), ex.getMessage());
            return;
        }

        // applyInvoiceStatus throws on an amount mismatch. Swallowing that inside
        // the transaction would mark it rollback-only and blow up at commit with
        // UnexpectedRollbackException, taking the whole 20-row batch with it — so
        // check the amount here and skip the row instead of throwing.
        if (invoice == null || !transaction.getExternalId().equals(invoice.getExternalId())) {
            return;
        }
        if (isPaidStatus(invoice.getStatus()) && paidAmountOf(invoice) != transaction.getAmount()) {
            log.error("Amount mismatch on reconcile for {}: expected {}, Xendit reported {}",
                    transaction.getExternalId(), transaction.getAmount(), paidAmountOf(invoice));
            return;
        }
        applyInvoiceStatus(transaction, invoice);
    }

    @Transactional
    public void reconcilePendingPayments() {
        paymentTransactionRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")
                .forEach(this::refreshPaymentFromXendit);
    }

    private static boolean isPaidStatus(String status) {
        if (status == null) return false;
        String s = status.toUpperCase();
        return "PAID".equals(s) || "SETTLED".equals(s);
    }

    private static long paidAmountOf(XenditInvoiceCallback callback) {
        if (callback.getPaidAmount() != null) return callback.getPaidAmount();
        if (callback.getAmount() != null) return callback.getAmount();
        return 0;
    }

    private void applyInvoiceStatus(PaymentTransaction transaction, XenditInvoiceCallback callback) {
        String status = callback.getStatus().toUpperCase();

        if ("PAID".equals(transaction.getStatus())) {
            return;
        }

        if (isPaidStatus(status)) {
            if (paidAmountOf(callback) != transaction.getAmount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nominal pembayaran tidak sesuai");
            }

            transaction.setStatus("PAID");
            transaction.setPaidAt(callback.getPaidAt() != null ? callback.getPaidAt() : Instant.now());
            transaction.setPaymentMethod(callback.getPaymentMethod());
            transaction.setPaymentChannel(callback.getPaymentChannel());
            if (callback.getId() != null) {
                transaction.setProviderTransactionId(callback.getId());
            }
            paymentTransactionRepository.save(transaction);
            activatePremium(transaction.getUserId());
            log.info("Xendit payment {} activated PRO for user {}", callback.getExternalId(), transaction.getUserId());
            sendBillingInboxNotification(
                    transaction.getUserId(),
                    "Pembayaran Berhasil - DevFlow Premium Aktif!",
                    "Paket Premium Anda telah aktif selama 30 hari. Nikmati akses tanpa batas ke semua fitur DevFlow.",
                    "HIGH",
                    frontendUrl + "/select-workspace"
            );
            return;
        }

        if ("EXPIRED".equals(status) || "FAILED".equals(status)) {
            if (status.equals(transaction.getStatus())) return;
            transaction.setStatus(status);
            paymentTransactionRepository.save(transaction);
            String title = "EXPIRED".equals(status)
                    ? "Invoice Kedaluwarsa"
                    : "Pembayaran Gagal";
            String content = "EXPIRED".equals(status)
                    ? "Invoice pembayaran DevFlow Premium Anda telah kedaluwarsa. Silakan buat checkout baru untuk melanjutkan."
                    : "Pembayaran DevFlow Premium Anda tidak berhasil diproses. Silakan coba lagi.";
            sendBillingInboxNotification(transaction.getUserId(), title, content, "NORMAL", null);
        }
    }

    private static final int PREMIUM_PERIOD_DAYS = 30;

    /**
     * Pembayaran memperpanjang langganan, bukan mereset.
     *
     * <p>Sebelumnya planEnd selalu ditimpa dengan now+30d. Kalau seseorang membayar
     * lagi sebelum periodenya habis — dua invoice terbuka sekaligus, atau perpanjang
     * lebih awal — sisa hari yang sudah dibayar hilang tanpa jejak. Sekarang
     * dihitung dari titik terakhir yang lebih jauh: akhir periode berjalan bila
     * masih aktif, atau sekarang bila sudah lewat.
     */
    private OwnerPlan activatePremium(UUID userId) {
        OwnerPlan ownerPlan = getOwnerPlan(userId);
        Instant now = Instant.now();
        Instant currentEnd = ownerPlan.getPlanEnd();
        Instant extendFrom = (currentEnd != null && currentEnd.isAfter(now)) ? currentEnd : now;

        ownerPlan.setPlanCode("PRO");
        ownerPlan.setActive(true);
        if (ownerPlan.getPlanStart() == null || currentEnd == null || currentEnd.isBefore(now)) {
            // Periode baru: mulai sekarang. Perpanjangan tidak menggeser tanggal mulai.
            ownerPlan.setPlanStart(now);
        }
        ownerPlan.setPlanEnd(extendFrom.plus(PREMIUM_PERIOD_DAYS, ChronoUnit.DAYS));
        return ownerPlanRepository.save(ownerPlan);
    }

    @Transactional
    public OwnerPlan upgradeToPremium(UUID userId) {
        return activatePremium(userId);
    }

    public Subscription getWorkspaceSubscription(UUID userId, UUID workspaceId) {
        enforceWorkspaceMember(userId, workspaceId);
        return subscriptionRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> createFreeSubscription(workspaceId));
    }

    /**
     * Subscription workspace hanya boleh dibaca anggotanya. Tanpa cek ini user mana pun
     * yang punya token valid bisa membaca subscription workspace lain dengan menebak UUID.
     * Query langsung ke workspace_members karena semua service berbagi database yuu_saas.
     */
    private void enforceWorkspaceMember(UUID userId, UUID workspaceId) {
        List<String> roles = jdbcTemplate.query(
                "SELECT role FROM workspace_members WHERE workspace_id = ? AND user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString("role"), workspaceId, userId);
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda bukan anggota workspace ini");
        }
    }

    @Transactional
    public Subscription createFreeSubscription(UUID workspaceId) {
        Plan freePlan = planRepository.findByCode("FREE")
                .orElseGet(() -> planRepository.save(Plan.builder()
                        .code("FREE")
                        .name("Free Plan")
                        .description("Paket gratis untuk tim kecil")
                        .priceAmount(0)
                        .billingInterval("MONTHLY")
                        .maxWorkspaces(3)
                        .maxProjectsPerWorkspace(3)
                        .maxMembersPerWorkspace(5)
                        .maxStorageGb(1)
                        .githubRepos(1)
                        .milestonesPerProject(3)
                        .build()));

        Subscription subscription = Subscription.builder()
                .workspaceId(workspaceId)
                .planId(freePlan.getId())
                .status("ACTIVE")
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(Instant.now().plus(3650, ChronoUnit.DAYS))
                .build();

        return subscriptionRepository.save(subscription);
    }
}
