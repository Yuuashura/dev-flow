package com.saas.auth_service.repository;

import com.saas.auth_service.entity.RefreshSession;
import com.saas.auth_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    Optional<RefreshSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    /** Tanpa filter revokedAt: changePassword mencabut semua sesi lebih dulu, lalu
     *  memulihkan sesi perangkat ini — jadi barisnya sudah ter-revoke saat dicari. */
    Optional<RefreshSession> findByTokenHash(String tokenHash);

    List<RefreshSession> findAllByUserAndRevokedAtIsNull(User user);

    /** Sesi kedaluwarsa disaring di query, bukan setelah barisnya terbaca. Varian lama
     *  menarik semua sesi yang belum dicabut lalu membuang yang kedaluwarsa di Java —
     *  biaya penuh untuk baris yang dijamin dibuang. */
    List<RefreshSession> findAllByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
            User user, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshSession r SET r.revokedAt = :now WHERE r.user = :user AND r.revokedAt IS NULL")
    int revokeAllByUser(User user, Instant now);

    @Modifying
    @Query("DELETE FROM RefreshSession r WHERE r.expiresAt < :now")
    int deleteExpired(Instant now);
}
