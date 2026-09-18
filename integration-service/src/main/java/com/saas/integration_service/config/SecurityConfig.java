package com.saas.integration_service.config;

import com.saas.integration_service.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Tanpa entry point eksplisit, Spring Security memakai Http403ForbiddenEntryPoint:
                // token kedaluwarsa atau rusak dijawab 403. Interceptor refresh di frontend hanya
                // bereaksi pada 401, jadi sesi mati keras tiap access token habis alih-alih
                // di-refresh. Bentuk body disamakan dengan GlobalExceptionHandler.
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                            "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Sesi tidak valid atau sudah berakhir.\",\"details\":[]}}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/integrations/github/webhook", "/actuator/health", "/error").permitAll()
                        // Spesifikasi OpenAPI diambil gateway untuk disajikan di
                        // Swagger UI. UI-nya hanya ada di gateway, jadi
                        // /swagger-ui/** sengaja TIDAK dibuka di sini.
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
