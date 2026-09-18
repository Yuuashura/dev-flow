package com.saas.auth_service.repository;

import com.saas.auth_service.entity.OAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {

    Optional<OAuthState> findByStateAndExpiresAtAfter(String state, Instant now);

    @Modifying
    @Query("DELETE FROM OAuthState o WHERE o.expiresAt < :now")
    int deleteExpired(Instant now);
}
