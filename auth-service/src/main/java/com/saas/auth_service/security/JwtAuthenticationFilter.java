package com.saas.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                UUID userId = tokenProvider.getUserIdFromToken(jwt);
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);

                // UserPrincipal memetakan status akun ke isEnabled()/isAccountNonLocked(),
                // tapi filter ini dulu membangun Authentication langsung dan tidak pernah
                // menanyakannya. Akibatnya access token milik akun yang baru disuspend
                // tetap diterima sampai token itu kedaluwarsa sendiri — padahal DB-nya
                // sudah dibaca di baris atas, jadi jawabannya ada di tangan.
                if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                    log.warn("Menolak token untuk akun tidak aktif: userId={}", userId);
                    SecurityContextHolder.clearContext();
                } else {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            // Jalur ini terbuka untuk siapa pun tanpa autentikasi, jadi token rusak
            // adalah kejadian normal, bukan kesalahan server. Sebelumnya di-log ERROR
            // dengan stack trace penuh: siapa pun bisa membanjiri log, dan pesan
            // parser-nya ikut tertulis. Request tetap lanjut sebagai anonim dan
            // ditolak entry point sebagai 401.
            log.debug("Token tidak dapat diverifikasi: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
