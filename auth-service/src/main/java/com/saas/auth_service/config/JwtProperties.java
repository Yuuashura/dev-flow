package com.saas.auth_service.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

    // No default: a committed fallback signing key means any service that
    // forgets to set JWT_SECRET silently trusts tokens anyone can forge.
    // @NotBlank juga menangkap kasus ${JWT_SECRET} yang tidak terselesaikan —
    // tanpa itu literal "${JWT_SECRET}" dipakai sebagai kunci penandatangan.
    @NotBlank
    private String secret;
    private long accessTokenExpirationMs = 900000; // 15 minutes
    private long refreshTokenExpirationMs = 604800000; // 7 days
    private String issuer = "devflow-auth-service";
}
