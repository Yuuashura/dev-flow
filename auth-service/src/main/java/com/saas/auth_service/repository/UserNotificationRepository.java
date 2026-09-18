package com.saas.auth_service.repository;

import com.saas.auth_service.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    Page<UserNotification> findByUserIdAndStatusNot(UUID userId, String status, Pageable pageable);

    Page<UserNotification> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);

    Page<UserNotification> findByUserIdAndTypeAndStatusNot(UUID userId, String type, String excludedStatus, Pageable pageable);

    Optional<UserNotification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, String status);

    @Modifying
    @Query("UPDATE UserNotification n SET n.status = 'READ', n.readAt = :now WHERE n.userId = :userId AND n.status = 'UNREAD'")
    int markAllAsRead(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserNotification n SET n.status = 'ARCHIVED' WHERE n.userId = :userId AND n.status = 'READ'")
    int archiveAllRead(@Param("userId") UUID userId);
}
