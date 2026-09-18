package com.saas.billing_service.repository;

import com.saas.billing_service.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByExternalIdAndUserId(String externalId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findByExternalId(String externalId);

    Page<PaymentTransaction> findByUserId(UUID userId, Pageable pageable);

    Page<PaymentTransaction> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);

    List<PaymentTransaction> findTop20ByStatusOrderByCreatedAtAsc(String status);
}
