package com.saas.auth_service.repository;

import com.saas.auth_service.entity.GithubConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GithubConnectionRepository extends JpaRepository<GithubConnection, UUID> {
    Optional<GithubConnection> findByUserId(UUID userId);
}
