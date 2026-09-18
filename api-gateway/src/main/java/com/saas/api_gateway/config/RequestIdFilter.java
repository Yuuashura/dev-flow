package com.saas.api_gateway.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Menerbitkan X-Request-ID di pintu masuk dan meneruskannya ke service hilir.
 *
 * <p>WebFilter, bukan OncePerRequestFilter: gateway ini reaktif (Spring Cloud Gateway),
 * jadi filter servlet tidak pernah dipanggil di sini.
 *
 * <p>Karena gateway adalah titik masuk pertama, id yang diterbitkan di sini yang
 * dipakai seluruh rantai panggilan. Service hilir memakai ulang lewat
 * RequestIdFilter masing-masing, jadi satu id merangkai seluruh jejak.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

    public static final String HEADER = "X-Request-ID";
    private static final int MAX_LENGTH = 64;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = sanitize(exchange.getRequest().getHeaders().getFirst(HEADER));

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.header(HEADER, requestId))
                .build();
        mutated.getResponse().getHeaders().set(HEADER, requestId);

        return chain.filter(mutated)
                // MDC tidak bisa dipakai lugas di jalur reaktif: satu request berpindah
                // thread beberapa kali. Id-nya diikat ke Reactor context, dan yang
                // benar-benar mencatatnya adalah service hilir yang memang servlet.
                .contextWrite(ctx -> ctx.put(HEADER, requestId));
    }

    /** Header ditulis klien, jadi CR/LF harus hilang — kalau tidak, penyerang bisa
     *  menyisipkan baris log palsu di service hilir. */
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
