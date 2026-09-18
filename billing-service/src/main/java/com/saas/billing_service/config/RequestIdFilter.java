package com.saas.billing_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Memberi setiap request satu id yang ikut ke semua baris log.
 *
 * <p>Dengan tujuh service yang saling memanggil, satu kegagalan meninggalkan jejak di
 * beberapa aliran log sekaligus dan tidak ada yang menghubungkannya. Satu-satunya alat
 * yang tersedia selama ini adalah membaca tujuh log berdampingan dan menebak baris
 * mana milik permintaan yang sama — itu berjam-jam kerja untuk satu insiden.
 *
 * <p>Id-nya dipakai ulang bila sudah ada di header, jadi rantai panggilan antar service
 * memakai id yang sama dari ujung ke ujung. GlobalExceptionHandler sudah membaca
 * X-Request-ID untuk field requestId di body error, jadi id yang dilihat user di layar
 * adalah id yang sama yang dicari di log.
 *
 * <p>Order dipasang paling awal supaya id sudah ada sebelum filter lain — termasuk
 * filter JWT — sempat mencatat apa pun.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    /** Batas panjang: nilai ini masuk ke setiap baris log, dan header dikendalikan
     *  klien. Tanpa batas, satu request bisa membanjiri log dengan satu id raksasa. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = sanitize(incoming);

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Wajib: thread dikembalikan ke pool, dan MDC yang tertinggal akan
            // menempel di request berikutnya yang kebetulan memakai thread ini.
            MDC.remove(MDC_KEY);
        }
    }

    /** Hanya karakter aman untuk log. Header ini ditulis klien, jadi CR/LF harus
     *  hilang — kalau tidak, penyerang bisa menyisipkan baris log palsu. */
    private static String sanitize(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String cleaned = incoming.trim().replaceAll("[^A-Za-z0-9._-]", "");
        if (cleaned.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
