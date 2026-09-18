package com.saas.billing_service.repository;

import com.saas.billing_service.entity.OwnerPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OwnerPlanRepository extends JpaRepository<OwnerPlan, UUID> {
    Optional<OwnerPlan> findByUserId(UUID userId);
}
