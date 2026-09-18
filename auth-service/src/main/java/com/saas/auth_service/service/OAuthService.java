package com.saas.auth_service.service;

import com.saas.auth_service.exception.AppException;
import com.saas.auth_service.config.GitHubOAuthProperties;
import com.saas.auth_service.config.GoogleOAuthProperties;
import com.saas.auth_service.config.JwtProperties;
import com.saas.auth_service.dto.AuthResponse;
import com.saas.auth_service.dto.UserDto;
import com.saas.auth_service.entity.*;
import com.saas.auth_service.repository.*;
import com.saas.auth_service.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final GoogleOAuthProperties googleProps;
    private final GitHubOAuthProperties githubProps;
    private final JwtProperties jwtProperties;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final OAuthStateRepository oAuthStateRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, String> getGoogleAuthorizeUrl() {
        String state = UUID.randomUUID().toString();

        OAuthState oAuthState = OAuthState.builder()
                .state(state)
                .provider(OAuthProvider.GOOGLE)
                .redirectUri(googleProps.getRedirectUri())
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        oAuthStateRepository.save(oAuthState);

        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + googleProps.getClientId()
                + "&redirect_uri=" + googleProps.getRedirectUri()
                + "&response_type=code"
                + "&scope=openid%20email%20profile"
                + "&state=" + state
                + "&access_type=offline"
                + "&prompt=consent";

        return Map.of("authUrl", authUrl, "state", state);
    }

    public Map<String, String> getGitHubAuthorizeUrl() {
        String state = UUID.randomUUID().toString();

        OAuthState oAuthState = OAuthState.builder()
                .state(state)
                .provider(OAuthProvider.GITHUB)
                .redirectUri(githubProps.getRedirectUri())
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        oAuthStateRepository.save(oAuthState);

        String authUrl = "https://github.com/login/oauth/authorize"
                + "?client_id=" + githubProps.getClientId()
                + "&redirect_uri=" + githubProps.getRedirectUri()
                + "&scope=read:user%20user:email"
                + "&state=" + state;

        return Map.of("authUrl", authUrl, "state", state);
    }

    @Transactional
    public AuthResponse handleGoogleCallback(String code, String state, String userAgent, String ipAddress) {
        OAuthState oAuthState = oAuthStateRepository.findByStateAndExpiresAtAfter(state, Instant.now())
                .orElseThrow(() -> new AppException(
                        "Sesi login Google tidak valid atau sudah kedaluwarsa. Silakan coba lagi.",
                        "OAUTH_STATE_INVALID", org.springframework.http.HttpStatus.BAD_REQUEST));
        if (oAuthState.getProvider() != OAuthProvider.GOOGLE) {
            throw new AppException("Provider tidak sesuai. Harap gunakan tombol Google.",
                    "OAUTH_PROVIDER_MISMATCH", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        oAuthStateRepository.delete(oAuthState);

        String googleAccessToken = exchangeCodeForToken(code);
        Map<String, Object> userInfo = fetchGoogleUserInfo(googleAccessToken);

        String googleId = (String) userInfo.get("sub");
        String email = (String) userInfo.get("email");
        String name = userInfo.containsKey("name") ? (String) userInfo.get("name") : email;
        String picture = (String) userInfo.get("picture");

        // processOAuthUser links by email. An unverified provider address would
        // therefore hand over any local account that happens to use it, so an
        // unverified Google email is refused outright.
        if (!Boolean.TRUE.equals(userInfo.get("email_verified"))) {
            throw new AppException("Email Google Anda belum terverifikasi oleh Google.",
                    "OAUTH_EMAIL_UNVERIFIED", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        return processOAuthUser(OAuthProvider.GOOGLE, googleId, email, name, picture, userAgent, ipAddress);
    }

    @Transactional
    public AuthResponse handleGitHubCallback(String code, String state, String userAgent, String ipAddress) {
        OAuthState oAuthState = oAuthStateRepository.findByStateAndExpiresAtAfter(state, Instant.now())
                .orElseThrow(() -> new AppException(
                        "Sesi login GitHub tidak valid atau sudah kedaluwarsa. Silakan coba lagi.",
                        "OAUTH_STATE_INVALID", org.springframework.http.HttpStatus.BAD_REQUEST));
        if (oAuthState.getProvider() != OAuthProvider.GITHUB) {
            throw new AppException("Provider tidak sesuai. Harap gunakan tombol GitHub.",
                    "OAUTH_PROVIDER_MISMATCH", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        oAuthStateRepository.delete(oAuthState);

        String githubAccessToken = exchangeGitHubCodeForToken(code);
        Map<String, Object> userInfo = fetchGitHubUserInfo(githubAccessToken);

        String githubId = String.valueOf(userInfo.get("id"));
        String login = (String) userInfo.get("login");
        String name = userInfo.get("name") != null ? (String) userInfo.get("name") : login;
        String avatarUrl = (String) userInfo.get("avatar_url");

        String email = (String) userInfo.get("email");
        if (email == null || email.isBlank()) {
            email = fetchGitHubPrimaryEmail(githubAccessToken);
        }
        if (email == null || email.isBlank()) {
            email = login + "@users.noreply.github.com";
        }

        return processOAuthUser(OAuthProvider.GITHUB, githubId, email, name, avatarUrl, userAgent, ipAddress);
    }

    private AuthResponse processOAuthUser(OAuthProvider provider, String providerUserId, String email, String name, String avatarUrl, String userAgent, String ipAddress) {
        Optional<UserIdentity> existingIdentity = userIdentityRepository
                .findByProviderAndProviderUserId(provider, providerUserId);

        User user;

        if (existingIdentity.isPresent()) {
            user = existingIdentity.get().getUser();
            UserIdentity identity = existingIdentity.get();
            identity.setLastUsedAt(Instant.now());
            userIdentityRepository.save(identity);
        } else {
            Optional<User> existingUser = userRepository.findByEmail(email);

            if (existingUser.isPresent()) {
                user = existingUser.get();
            } else {
                user = User.builder()
                        .email(email)
                        .fullName(name)
                        .avatarUrl(avatarUrl)
                        .emailVerified(true)
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.save(user);
            }

            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
            }
            if (user.getAvatarUrl() == null && avatarUrl != null) {
                user.setAvatarUrl(avatarUrl);
            }
            userRepository.save(user);

            UserIdentity identity = UserIdentity.builder()
                    .user(user)
                    .provider(provider)
                    .providerUserId(providerUserId)
                    .providerEmail(email)
                    .providerUsername(name)
                    .lastUsedAt(Instant.now())
                    .build();
            userIdentityRepository.save(identity);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException("Akun Anda telah dinonaktifkan", "ACCOUNT_DISABLED",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return buildAuthResponse(user, userAgent, ipAddress);
    }

    private String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", googleProps.getClientId());
        params.add("client_secret", googleProps.getClientSecret());
        params.add("redirect_uri", googleProps.getRedirectUri());
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "https://oauth2.googleapis.com/token",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        if (response.getBody() == null || !response.getBody().containsKey("access_token")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gagal menukar kode otorisasi Google. Coba masuk ulang.");
        }

        return (String) response.getBody().get("access_token");
    }

    private String exchangeGitHubCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", githubProps.getClientId());
        params.add("client_secret", githubProps.getClientSecret());
        params.add("redirect_uri", githubProps.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "https://github.com/login/oauth/access_token",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        if (response.getBody() == null || !response.getBody().containsKey("access_token")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gagal menukar kode otorisasi GitHub. Coba masuk ulang.");
        }

        return (String) response.getBody().get("access_token");
    }

    private Map<String, Object> fetchGoogleUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "https://www.googleapis.com/oauth2/v3/userinfo",
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        if (response.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gagal mengambil data profil dari Google.");
        }

        return response.getBody();
    }

    private Map<String, Object> fetchGitHubUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", "DevFlow-SaaS-App");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "https://api.github.com/user",
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        if (response.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gagal mengambil data profil dari GitHub.");
        }

        return response.getBody();
    }

    private String fetchGitHubPrimaryEmail(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("User-Agent", "DevFlow-SaaS-App");
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    "https://api.github.com/user/emails",
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            if (response.getBody() != null) {
                for (Map<String, Object> emailObj : response.getBody()) {
                    Boolean primary = (Boolean) emailObj.get("primary");
                    Boolean verified = (Boolean) emailObj.get("verified");
                    if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified)) {
                        return (String) emailObj.get("email");
                    }
                }
                // Only verified addresses. The previous fallback returned the first
                // email of any kind, and processOAuthUser links by email — so an
                // attacker could add a victim's address to their GitHub account,
                // leave it unverified, and take over the matching local account.
                for (Map<String, Object> emailObj : response.getBody()) {
                    if (Boolean.TRUE.equals(emailObj.get("verified"))) {
                        return (String) emailObj.get("email");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch GitHub user emails: {}", e.getMessage());
        }
        return null;
    }

    private AuthResponse buildAuthResponse(User user, String userAgent, String ipAddress) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(),
                user.getGlobalRole() != null ? user.getGlobalRole().name() : null);

        String rawRefreshToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawRefreshToken);

        RefreshSession refreshSession = RefreshSession.builder()
                .user(user)
                .tokenHash(hashedToken)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpirationMs(), ChronoUnit.MILLIS))
                .build();
        refreshSessionRepository.save(refreshSession);

        List<UserIdentity> identities = userIdentityRepository.findAllByUser(user);
        List<OAuthProvider> providers = identities.stream().map(UserIdentity::getProvider).toList();

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .globalRole(user.getGlobalRole())
                .identities(providers)
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .user(userDto)
                .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
