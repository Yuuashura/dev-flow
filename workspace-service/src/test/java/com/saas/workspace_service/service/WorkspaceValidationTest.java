package com.saas.workspace_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Helper ini dipakai jalur create DAN update. Sebelumnya hanya updateWorkspaceDetails
 * yang mengecek timezone dan panjang nama, sementara createWorkspace menyimpan mentah —
 * jadi nilai yang ditolak saat diedit justru bisa masuk saat dibuat.
 */
class WorkspaceValidationTest {

    @Test
    void nameIsTrimmedAndBounded() {
        assertEquals("Tim Produk", WorkspaceService.validateWorkspaceName("  Tim Produk  "));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateWorkspaceName("   "));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateWorkspaceName(null));
        assertThrows(ResponseStatusException.class,
                () -> WorkspaceService.validateWorkspaceName("x".repeat(WorkspaceService.MAX_WORKSPACE_NAME_LENGTH + 1)));
    }

    @Test
    void slugIsNormalisedAndPathSafe() {
        // Dipakai verbatim sebagai segmen URL, jadi huruf besar dinormalkan — tanpa itu
        // "Acme" dan "acme" jadi dua workspace dengan URL yang sama.
        assertEquals("tim-produk", WorkspaceService.validateSlug("  Tim-Produk  "));
        assertEquals("acme2026", WorkspaceService.validateSlug("ACME2026"));

        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateSlug("tim produk"));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateSlug("tim/produk"));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateSlug("-tim"));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateSlug("tim-"));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateSlug("tim--produk"));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateSlug("a"));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateSlug("x".repeat(101)));
    }

    @Test
    void timezoneMustBeAKnownZone() {
        assertEquals("Asia/Jakarta", WorkspaceService.validateTimezone("Asia/Jakarta"));
        assertNull(WorkspaceService.validateTimezone(null));
        assertNull(WorkspaceService.validateTimezone("   "));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateTimezone("Mars/Olympus"));
        assertThrows(ResponseStatusException.class,
                () -> WorkspaceService.validateTimezone("x".repeat(WorkspaceService.MAX_TIMEZONE_LENGTH + 1)));
    }

    @Test
    void businessTypeIsBoundedToItsColumn() {
        assertEquals("Agency", WorkspaceService.validateBusinessType(" Agency "));
        assertNull(WorkspaceService.validateBusinessType(""));
        assertDoesNotThrow(() -> WorkspaceService.validateBusinessType("x".repeat(WorkspaceService.MAX_BUSINESS_TYPE_LENGTH)));
        assertThrows(ResponseStatusException.class,
                () -> WorkspaceService.validateBusinessType("x".repeat(WorkspaceService.MAX_BUSINESS_TYPE_LENGTH + 1)));
    }

    @Test
    void logoHanyaMenerimaMediaInternal() {
        // Kontraknya berubah: logo tidak lagi menerima URL eksternal sama sekali,
        // sekarang harus menunjuk media yang diunggah ke auth-service. Tanpa ini,
        // seluruh pemeriksaan unggahan bisa dilewati dengan menaruh URL lewat API.
        String ref = "/api/v1/media/3f2504e0-4f89-41d3-9a0c-0305e82c3301";
        assertEquals(ref, WorkspaceService.validateLogoUrl(ref));
        assertNull(WorkspaceService.validateLogoUrl(null));
        assertNull(WorkspaceService.validateLogoUrl(""));

        // Dulu diterima; sekarang tidak. Host eksternal berarti setiap anggota yang
        // memuat halaman menembak server milik orang lain, dan isinya bisa berubah
        // kapan saja setelah lolos pemeriksaan.
        assertThrows(ResponseStatusException.class,
                () -> WorkspaceService.validateLogoUrl("https://cdn.example.com/a.png"));
        assertThrows(ResponseStatusException.class,
                () -> WorkspaceService.validateLogoUrl("javascript:alert(1)"));
        assertThrows(ResponseStatusException.class,
                () -> WorkspaceService.validateLogoUrl("data:text/html;base64,PHN2Zz4="));
        assertThrows(ResponseStatusException.class,
                () -> WorkspaceService.validateLogoUrl("/api/v1/media/../../etc/passwd"));
        assertThrows(ResponseStatusException.class,
                () -> WorkspaceService.validateLogoUrl("/api/v1/media/bukan-uuid"));
    }
}
