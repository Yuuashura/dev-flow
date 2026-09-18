package com.saas.project_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectServiceTaskValidationTest {

    private static final LocalDate PROJECT_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate PROJECT_TARGET = LocalDate.of(2026, 8, 31);

    @Test
    void validatesFlowScheduleAndEstimate() {
        LocalDate today = LocalDate.of(2026, 8, 11);

        assertDoesNotThrow(() -> ProjectService.validateTaskSchedule(today, today, new BigDecimal("1.25")));
        assertThrows(ResponseStatusException.class,
                () -> ProjectService.validateTaskSchedule(today.plusDays(1), today, BigDecimal.ONE));
        assertThrows(ResponseStatusException.class,
                () -> ProjectService.validateTaskSchedule(null, null, BigDecimal.ZERO));
        assertThrows(ResponseStatusException.class,
                () -> ProjectService.validateTaskSchedule(null, null, new BigDecimal("10001")));
    }

    @Test
    void flowMustStayInsideProjectWindow() {
        assertDoesNotThrow(() -> ProjectService.validateTaskSchedule(
                PROJECT_START, PROJECT_TARGET, BigDecimal.ONE, PROJECT_START, PROJECT_TARGET));

        // Mulai sebelum proyek dimulai.
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateTaskSchedule(
                PROJECT_START.minusDays(1), PROJECT_TARGET, BigDecimal.ONE, PROJECT_START, PROJECT_TARGET));

        // Selesai setelah target proyek.
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateTaskSchedule(
                PROJECT_START, PROJECT_TARGET.plusDays(1), BigDecimal.ONE, PROJECT_START, PROJECT_TARGET));

        // Hanya dueDate yang diisi, dan jatuh sebelum proyek mulai: celah "startDate null".
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateTaskSchedule(
                null, PROJECT_START.minusDays(3), BigDecimal.ONE, PROJECT_START, PROJECT_TARGET));

        // Hanya startDate yang diisi, dan lewat target proyek.
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateTaskSchedule(
                PROJECT_TARGET.plusDays(3), null, BigDecimal.ONE, PROJECT_START, PROJECT_TARGET));

        // Proyek tanpa jadwal tidak membatasi Flow.
        assertDoesNotThrow(() -> ProjectService.validateTaskSchedule(
                PROJECT_START, PROJECT_TARGET, BigDecimal.ONE, null, null));
    }

    @Test
    void validatesFlowTitle() {
        org.junit.jupiter.api.Assertions.assertEquals("Checkout",
                ProjectService.validateFlowTitle("  Checkout  "));
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateFlowTitle("   "));
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateFlowTitle(null));
        // Jalur update dulu melewatkan cek ini, jadi judul panjang jadi 500 dari driver.
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateFlowTitle("x".repeat(256)));
    }

    @Test
    void validatesProjectNameAndSchedule() {
        assertDoesNotThrow(() -> ProjectService.validateProjectSchedule("  Portal Klien  ", PROJECT_START, PROJECT_TARGET));

        // Nama dipangkas, bukan disimpan apa adanya.
        org.junit.jupiter.api.Assertions.assertEquals("Portal Klien",
                ProjectService.validateProjectSchedule("  Portal Klien  ", null, null));

        assertThrows(ResponseStatusException.class,
                () -> ProjectService.validateProjectSchedule("   ", null, null));
        assertThrows(ResponseStatusException.class,
                () -> ProjectService.validateProjectSchedule("x".repeat(256), null, null));
        assertThrows(ResponseStatusException.class,
                () -> ProjectService.validateProjectSchedule("Portal", PROJECT_TARGET, PROJECT_START));
    }
}
