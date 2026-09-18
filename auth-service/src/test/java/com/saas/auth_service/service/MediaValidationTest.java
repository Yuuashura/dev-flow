package com.saas.auth_service.service;

import com.saas.auth_service.exception.AppException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pengenalan tipe berkas dan penguncian rujukan gambar.
 *
 * <p>Intinya satu: tidak ada keputusan yang boleh diambil dari apa yang dikirim klien.
 * Nama berkas, ekstensi, dan header Content-Type semuanya dipilih penyerang.
 */
class MediaValidationTest {

    private static byte[] png() {
        // 8 byte signature PNG.
        return new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
    }

    private static byte[] jpeg() {
        return new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0 };
    }

    @Test
    void mengenaliPngDanJpegDariByte() {
        assertEquals("image/png", MediaService.sniff(png()));
        assertEquals("image/jpeg", MediaService.sniff(jpeg()));
    }

    @Test
    void menolakSvgWalaupunNamanyaPng() {
        // SVG adalah dokumen XML yang bisa memuat <script>. Menyajikannya dari origin
        // kita berarti skrip itu berjalan dengan hak penuh sesi pengguna — inilah
        // alasan pengenalan tipe dilakukan dari byte, bukan dari nama berkas.
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertNull(MediaService.sniff(svg));
    }

    @Test
    void menolakHtmlDanArsipYangMenyamar() {
        assertNull(MediaService.sniff("<!DOCTYPE html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
        // GIF ditolak: riwayat panjang sebagai pembawa polyglot, dan varian animasinya
        // tidak selamat melewati encode ulang.
        assertNull(MediaService.sniff("GIF89a".getBytes(StandardCharsets.UTF_8)));
        // ZIP/JAR — dasar dari polyglot semacam GIFAR.
        assertNull(MediaService.sniff(new byte[] { 'P', 'K', 0x03, 0x04, 0, 0 }));
        assertNull(MediaService.sniff(new byte[] { 0, 1, 2 }));
        assertNull(MediaService.sniff(new byte[0]));
    }

    @Test
    void signatureYangHampirBenarTetapDitolak() {
        // Satu byte meleset dari signature PNG.
        assertNull(MediaService.sniff(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0B, 0, 0 }));
        // Terlalu pendek untuk dipastikan.
        assertNull(MediaService.sniff(new byte[] { (byte) 0xFF, (byte) 0xD8 }));
    }

    @Test
    void rujukanGambarHanyaMenerimaMediaInternal() {
        String ref = "/api/v1/media/3f2504e0-4f89-41d3-9a0c-0305e82c3301";
        assertEquals(ref, AuthService.validateImageRef(ref));
        assertNull(AuthService.validateImageRef(null));
        assertNull(AuthService.validateImageRef("   "));

        // Justru inilah yang ditutup: tanpa ini seluruh pemeriksaan unggahan bisa
        // dilewati hanya dengan menaruh URL langsung lewat API.
        assertThrows(AppException.class, () -> AuthService.validateImageRef("https://evil.example.com/a.png"));
        assertThrows(AppException.class, () -> AuthService.validateImageRef("//evil.example.com/a.png"));
        assertThrows(AppException.class, () -> AuthService.validateImageRef("javascript:alert(1)"));
        // Path traversal dan UUID yang tidak berbentuk.
        assertThrows(AppException.class, () -> AuthService.validateImageRef("/api/v1/media/../../etc/passwd"));
        assertThrows(AppException.class, () -> AuthService.validateImageRef("/api/v1/media/bukan-uuid"));
    }

    @Test
    void sha256StabilDanSepanjangDuaPuluhEnamHeksa() {
        String a = MediaService.sha256Hex(png());
        String b = MediaService.sha256Hex(png());
        assertEquals(a, b);
        assertEquals(64, a.length());
        org.junit.jupiter.api.Assertions.assertNotEquals(a, MediaService.sha256Hex(jpeg()));
    }
}
