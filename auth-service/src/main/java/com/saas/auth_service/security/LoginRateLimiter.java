package com.saas.auth_service.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Membatasi percobaan login.
 *
 * <p>Tiga variabel — LOGIN_RATE_LIMIT_EMAIL_IP, LOGIN_RATE_LIMIT_IP, dan
 * LOGIN_RATE_LIMIT_WINDOW_SECONDS — sudah ada di .env sejak lama tapi tidak pernah
 * dibaca satu baris kode pun. Endpoint login karena itu terbuka untuk dicoba tanpa
 * batas, dan keberadaan variabelnya justru lebih buruk daripada tidak ada sama sekali:
 * siapa pun yang membaca konfigurasi menyimpulkan fiturnya sudah jalan.
 *
 * <p><b>Dua kunci, bukan satu.</b> Yang per-IP menahan satu penyerang mencoba banyak
 * akun; yang per-email+IP menahan satu akun digempur. Salah satu saja bisa diputari —
 * batas per-email saja dilewati dengan menggilir alamat, batas per-IP saja dilewati
 * dengan botnet. Keduanya perlu.
 *
 * <p><b>Gagal tertutup.</b> Kalau Redis mati, login ditolak sementara, bukan
 * diloloskan. Pembatas laju yang membuka pintu saat backend-nya tumbang adalah
 * pembatas laju yang bisa dimatikan penyerang lebih dulu.
 */
@Component
@Slf4j
public class LoginRateLimiter {

    private final StringRedisTemplate redis;

    /** Percobaan per kombinasi email+IP dalam satu jendela. */
    @Value("${app.login-rate-limit.email-ip:10}")
    private int maxPerEmailAndIp;

    /** Percobaan per IP dalam satu jendela, apa pun emailnya. */
    @Value("${app.login-rate-limit.ip:30}")
    private int maxPerIp;

    @Value("${app.login-rate-limit.window-seconds:900}")
    private long windowSeconds;

    /**
     * Berapa banyak proxy tepercaya di depan service ini. Menentukan entri mana di
     * X-Forwarded-For yang benar-benar milik klien.
     *
     * <p>Header itu bisa ditulis siapa saja. Mengambil entri pertama begitu saja
     * berarti penyerang cukup mengirim `X-Forwarded-For: 1.2.3.4` yang berganti-ganti
     * untuk mendapat kuota tak terbatas. Yang benar adalah menghitung dari kanan
     * sebanyak hop yang kita percayai — entri di sebelah kanan ditulis infrastruktur
     * kita sendiri dan tidak bisa dipalsukan klien.
     */
    @Value("${app.trusted-proxy-hops:1}")
    private int trustedProxyHops;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Dipanggil sebelum kredensial diperiksa. Melempar 429 bila kuota habis. */
    public void checkAndRecord(String email, HttpServletRequest request) {
        String ip = clientIp(request);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        // Email di-hash: kunci Redis muncul di log dan alat monitoring, dan alamat
        // email adalah data pribadi. Hash tetap stabil untuk penghitungan.
        String emailKey = "rl:login:eip:" + sha256(normalizedEmail + "|" + ip);
        String ipKey = "rl:login:ip:" + ip;

        long perEmail = hit(emailKey);
        long perIp = hit(ipKey);

        if (perEmail > maxPerEmailAndIp || perIp > maxPerIp) {
            log.warn("Batas percobaan login terlampaui dari {} (email+ip={}, ip={})",
                    ip, perEmail, perIp);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Terlalu banyak percobaan masuk. Coba lagi dalam "
                            + (windowSeconds / 60) + " menit.");
        }
    }

    /**
     * Menghapus hitungan setelah login berhasil, supaya pemakai sah yang sempat salah
     * ketik beberapa kali tidak terkunci sisa jendelanya.
     */
    public void clear(String email, HttpServletRequest request) {
        String ip = clientIp(request);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            redis.delete("rl:login:eip:" + sha256(normalizedEmail + "|" + ip));
        } catch (RuntimeException e) {
            // Gagal membersihkan tidak berbahaya: hitungannya kedaluwarsa sendiri.
            log.debug("Gagal membersihkan hitungan rate limit: {}", e.getMessage());
        }
    }

    /** INCR lalu pasang TTL pada kenaikan pertama. Mengembalikan hitungan terbaru. */
    private long hit(String key) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count == null ? 1L : count;
        } catch (RuntimeException e) {
            // Gagal tertutup. Alternatifnya adalah membiarkan penyerang mematikan
            // Redis lebih dulu untuk membuka login sepenuhnya.
            log.error("Redis tidak dapat dihubungi saat memeriksa batas login", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Layanan masuk sedang tidak tersedia. Coba beberapa saat lagi.");
        }
    }

    /**
     * IP klien sebenarnya.
     *
     * <p>getRemoteAddr() di belakang gateway mengembalikan IP gateway — satu nilai
     * untuk semua orang, yang akan mengunci seluruh pengguna begitu satu penyerang
     * menghabiskan kuota. X-Forwarded-For dibaca dari kanan sebanyak hop tepercaya.
     */
    public String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && trustedProxyHops > 0) {
            String[] parts = forwarded.split(",");
            int index = parts.length - trustedProxyHops;
            if (index >= 0 && index < parts.length) {
                String candidate = parts[index].trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 wajib tersedia di setiap JVM", e);
        }
    }
}
