package com.saas.auth_service.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "google")
@Validated
@Getter
@Setter
public class GoogleOAuthProperties {
    // Tanpa @NotBlank, placeholder ${GOOGLE_CLIENT_ID} yang tidak terisi diikat
    // sebagai string literal dan baru ketahuan sebagai URL OAuth rusak di browser.
    @NotBlank
    private String clientId;
    @NotBlank
    private String clientSecret;
    @NotBlank
    private String redirectUri;
}
