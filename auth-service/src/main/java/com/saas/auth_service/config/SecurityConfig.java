package com.saas.auth_service.config;

import com.saas.auth_service.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Empat service lain sudah punya ini; auth-service terlewat, jadi
                // justru service yang memegang /api/v1/auth/me — endpoint yang dipanggil
                // frontend saat memuat — masih menjawab 403 lewat Http403ForbiddenEntryPoint.
                // Interceptor refresh hanya bereaksi pada 401, jadi sesi tetap mati keras
                // di sini meski sudah diperbaiki di tempat lain.
                //
                // accessDeniedHandler dipisah: 403 tetap benar untuk pemakai yang sudah
                // terautentikasi tapi bukan SUPER_ADMIN, dan itu TIDAK boleh memicu refresh.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Sesi tidak valid atau sudah berakhir.\",\"details\":[]}}");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Anda tidak punya izin untuk aksi ini.\",\"details\":[]}}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/error",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/refresh",
                                "/oauth2/**",
                                "/api/v1/notifications/internal",
                                "/actuator/health"
                        ).permitAll()
                        // Hanya GET: avatar muncul di daftar anggota dan komentar,
                        // jadi menuntut token akan memecah tampilan untuk semua orang.
                        // POST /api/v1/media tetap butuh autentikasi.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/media/*").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        // Spesifikasi OpenAPI diambil gateway untuk disajikan di
                        // Swagger UI. UI-nya hanya ada di gateway, jadi
                        // /swagger-ui/** sengaja TIDAK dibuka di sini.
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Daftar origin yang sama dengan api-gateway. Service ini satu-satunya yang punya
     *  kebijakan CORS sendiri, dan kebijakannya dulu "*", jadi pembatasan yang sengaja
     *  dipasang di gateway tidak berlaku untuk siapa pun yang menembak port 8081. */
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Wajib sekarang origin-nya tidak lagi "*": browser menolak kombinasi
        // allowCredentials dengan wildcard, jadi dulu ini memang tidak bisa dipasang.
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
