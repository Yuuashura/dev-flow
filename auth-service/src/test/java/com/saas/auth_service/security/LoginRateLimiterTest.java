package com.saas.auth_service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Penentuan IP klien.
 *
 * <p>Ini bagian yang paling mudah salah dan paling mahal kalau salah. X-Forwarded-For
 * ditulis klien; membacanya dari kiri berarti penyerang cukup mengirim alamat palsu
 * yang berganti-ganti untuk mendapat kuota tak terbatas — pembatas lajunya jadi
 * hiasan. Membaca dari kanan sebanyak hop tepercaya memakai entri yang ditulis
 * infrastruktur sendiri, yang tidak bisa dipalsukan.
 */
class LoginRateLimiterTest {

    private LoginRateLimiter limiterWithHops(int hops) {
        LoginRateLimiter limiter = new LoginRateLimiter(null);
        ReflectionTestUtils.setField(limiter, "trustedProxyHops", hops);
        return limiter;
    }

    private HttpServletRequest request(String forwardedFor, String remoteAddr) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
        return req;
    }

    @Test
    void mengambilEntriDariKananSebanyakHopTepercaya() {
        // Satu proxy di depan: entri terakhir ditulis proxy itu dan tepercaya.
        assertEquals("203.0.113.9",
                limiterWithHops(1).clientIp(request("1.1.1.1, 203.0.113.9", "10.0.0.1")));
    }

    @Test
    void alamatPalsuDariKlienDiabaikan() {
        // Penyerang mengirim entri palsu di depan. Dengan satu hop tepercaya, yang
        // dipakai tetap entri yang ditulis proxy kita — bukan yang dia kirim.
        String spoofed = "evil-1, evil-2, evil-3, 203.0.113.9";
        assertEquals("203.0.113.9", limiterWithHops(1).clientIp(request(spoofed, "10.0.0.1")));

        // Memakai entri pertama akan memberi nilai berbeda tiap request dan membuat
        // kuota tidak pernah habis — inilah yang dihindari.
        assertNotEquals("evil-1", limiterWithHops(1).clientIp(request(spoofed, "10.0.0.1")));
    }

    @Test
    void jatuhKeRemoteAddrSaatHeaderTidakAdaAtauKosong() {
        assertEquals("10.0.0.1", limiterWithHops(1).clientIp(request(null, "10.0.0.1")));
        assertEquals("10.0.0.1", limiterWithHops(1).clientIp(request("   ", "10.0.0.1")));
        assertEquals("10.0.0.1", limiterWithHops(1).clientIp(request(", ", "10.0.0.1")));
    }

    @Test
    void nolHopTepercayaBerartiHeaderTidakDipercayaSamaSekali() {
        assertEquals("10.0.0.1", limiterWithHops(0).clientIp(request("1.2.3.4", "10.0.0.1")));
    }

    @Test
    void hopLebihBanyakDaripadaEntriTidakMembuatnyaMeledak() {
        // Konfigurasi salah tidak boleh melempar; jatuh ke remoteAddr yang aman.
        assertEquals("10.0.0.1", limiterWithHops(5).clientIp(request("1.1.1.1", "10.0.0.1")));
    }

    @Test
    void remoteAddrNullTetapMenghasilkanKunciYangValid() {
        assertEquals("unknown", limiterWithHops(1).clientIp(request(null, null)));
    }

    @Test
    void kunciHitunganMenyembunyikanAlamatEmail() {
        // Kunci Redis muncul di log dan alat monitoring; alamat email adalah data
        // pribadi dan tidak boleh ikut terbaca di sana.
        String hash = LoginRateLimiter.sha256("orang@example.com|203.0.113.9");
        assertEquals(64, hash.length());
        org.junit.jupiter.api.Assertions.assertFalse(hash.contains("orang"));
        assertEquals(hash, LoginRateLimiter.sha256("orang@example.com|203.0.113.9"));
        assertNotEquals(hash, LoginRateLimiter.sha256("lain@example.com|203.0.113.9"));
    }
}
