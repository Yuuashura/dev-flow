package com.saas.auth_service.security;

import com.saas.auth_service.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, String email, String globalRole) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpirationMs());

        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiration(expiryDate);

        if (globalRole != null) {
            builder.claim("globalRole", globalRole);
        }

        return builder.signWith(getSigningKey()).compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            // DEBUG, bukan ERROR: token kedaluwarsa adalah kejadian rutin dan jalur ini
            // terbuka tanpa autentikasi, jadi level ERROR membuat siapa pun bisa
            // membanjiri log operasional dengan mengirim sampah berulang.
            log.debug("Invalid JWT token: {}", ex.getMessage());
        }
        return false;
    }
}
