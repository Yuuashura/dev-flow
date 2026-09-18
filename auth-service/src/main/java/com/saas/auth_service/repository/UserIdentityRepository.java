package com.saas.auth_service.repository;

import com.saas.auth_service.entity.OAuthProvider;
import com.saas.auth_service.entity.User;
import com.saas.auth_service.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    Optional<UserIdentity> findByUserAndProvider(User user, OAuthProvider provider);

    List<UserIdentity> findAllByUser(User user);

    boolean existsByUserAndProvider(User user, OAuthProvider provider);
}
