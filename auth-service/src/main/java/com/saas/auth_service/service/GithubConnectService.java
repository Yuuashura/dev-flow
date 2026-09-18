package com.saas.auth_service.service;

import com.saas.auth_service.config.GitHubOAuthProperties;
import com.saas.auth_service.entity.GithubConnection;
import com.saas.auth_service.entity.OAuthState;
import com.saas.auth_service.entity.OAuthProvider;
import com.saas.auth_service.exception.AppException;
import com.saas.auth_service.repository.GithubConnectionRepository;
import com.saas.auth_service.repository.OAuthStateRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GithubConnectService {

    private final GitHubOAuthProperties githubProps;
    private final OAuthStateRepository oAuthStateRepository;
    private final GithubConnectionRepository githubConnectionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, String> getConnectAuthorizeUrl() {
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
                + "&scope=read:user%20repo"
                + "&state=" + state;

        return Map.of("authUrl", authUrl, "state", state);
    }

    @Transactional
    public Map<String, Object> handleConnectCallback(String code, String state, UUID userId) {
        OAuthState oAuthState = oAuthStateRepository.findByStateAndExpiresAtAfter(state, Instant.now())
                .orElseThrow(() -> new AppException(
                        "Sesi koneksi GitHub tidak valid atau sudah kedaluwarsa. Silakan coba lagi.",
                        "OAUTH_STATE_INVALID", HttpStatus.BAD_REQUEST));

        oAuthStateRepository.delete(oAuthState);

        String accessToken = exchangeGitHubCodeForToken(code);
        Map<String, Object> userInfo = fetchGitHubUserInfo(accessToken);

        String login = (String) userInfo.get("login");
        if (login == null || login.isBlank()) {
            throw new AppException("Gagal membaca profil GitHub Anda.", "GITHUB_FETCH_FAILED",
                    HttpStatus.BAD_GATEWAY);
        }

        GithubConnection connection = githubConnectionRepository.findByUserId(userId)
                .map(existing -> {
                    existing.setGithubLogin(login);
                    existing.setAccessToken(accessToken);
                    existing.setScope("read:user,repo");
                    return existing;
                })
                .orElseGet(() -> GithubConnection.builder()
                        .userId(userId)
                        .githubLogin(login)
                        .accessToken(accessToken)
                        .scope("read:user,repo")
                        .build());

        githubConnectionRepository.save(connection);

        log.info("GitHub account {} connected to user {}", login, userId);
        return Map.of("connected", true, "githubLogin", login);
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
            throw new AppException("Gagal menukar kode GitHub menjadi token.", "OAUTH_TOKEN_EXCHANGE_FAILED",
                    HttpStatus.BAD_GATEWAY);
        }

        return (String) response.getBody().get("access_token");
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
}