package com.saas.project_service.service;

import com.saas.project_service.dto.TimeEntryRequest;
import com.saas.project_service.entity.Project;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Semua batas di sini mencerminkan CHECK constraint dan lebar kolom di
 * V8__create_time_entries.sql. Sebelum perbaikan ini tidak ada satu pun yang dicek di
 * kode: anotasi DTO tidak pernah jalan karena controller memakai @RequestBody tanpa
 * @Valid, jadi Postgres yang menolaknya — sebagai 500.
 */
class TimeTrackingValidationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);
    private static final LocalDate PROJECT_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate PROJECT_TARGET = LocalDate.of(2026, 9, 30);

    private static Project project(LocalDate start, LocalDate target) {
        Project p = new Project();
        p.setStartDate(start);
        p.setTargetDate(target);
        return p;
    }

    private static TimeEntryRequest request() {
        TimeEntryRequest r = new TimeEntryRequest();
        r.setEntryDate(LocalDate.of(2026, 9, 10));
        r.setHours(new BigDecimal("2.5"));
        r.setEntryType("feature");
        r.setFeatureName("  Checkout  ");
        return r;
    }

    @Test
    void normalisesEntryTypeAndAcceptsValidRequest() {
        assertEquals("FEATURE", TimeTrackingService.validateTimeEntry(request(), project(PROJECT_START, PROJECT_TARGET)));
    }

    @Test
    void rejectsUnknownOrMissingEntryType() {
        TimeEntryRequest r = request();
        r.setEntryType("DEPLOY");
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateTimeEntry(r, null));

        // Dulu di-dereference tanpa cek null: NullPointerException, bukan 400.
        r.setEntryType(null);
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateTimeEntry(r, null));

        r.setEntryType("   ");
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateTimeEntry(r, null));
    }

    @Test
    void boundsHoursToTheColumnCheckConstraint() {
        TimeEntryRequest r = request();

        r.setHours(null);
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateTimeEntry(r, null));

        r.setHours(BigDecimal.ZERO);
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateTimeEntry(r, null));

        r.setHours(new BigDecimal("-1"));
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateTimeEntry(r, null));

        r.setHours(new BigDecimal("24.01"));
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateTimeEntry(r, null));

        // Tepat di batas masih boleh.
        r.setHours(new BigDecimal("24"));
        assertDoesNotThrow(() -> TimeTrackingService.validateTimeEntry(r, null));
    }

    @Test
    void boundsFeatureNameToTheColumnWidth() {
        TimeEntryRequest r = request();
        r.setFeatureName("x".repeat(256));
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateTimeEntry(r, null));
    }

    @Test
    void trimsFeatureNameAndTreatsBlankAsAbsent() {
        assertEquals("Checkout", TimeTrackingService.trimToNull("  Checkout  "));
        assertNull(TimeTrackingService.trimToNull("   "));
        assertNull(TimeTrackingService.trimToNull(null));
    }

    @Test
    void entryDateMustSitInsideTheProjectWindow() {
        assertDoesNotThrow(() -> TimeTrackingService.validateEntryDate(
                LocalDate.of(2026, 9, 10), PROJECT_START, PROJECT_TARGET, TODAY));

        // Sebelum proyek mulai.
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateEntryDate(
                PROJECT_START.minusDays(1), PROJECT_START, PROJECT_TARGET, TODAY));

        // Setelah target proyek. Target di sini masih di masa depan relatif TODAY,
        // jadi yang menolak benar-benar aturan jendela proyek, bukan aturan masa depan.
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateEntryDate(
                PROJECT_TARGET.plusDays(1), PROJECT_START, PROJECT_TARGET, TODAY));
    }

    @Test
    void entryDateRejectsFutureAndAncientDates() {
        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateEntryDate(
                TODAY.plusDays(1), null, null, TODAY));

        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateEntryDate(
                TODAY.minusDays(TimeTrackingService.MAX_BACKDATE_DAYS + 1), null, null, TODAY));

        assertThrows(ResponseStatusException.class, () -> TimeTrackingService.validateEntryDate(
                null, null, null, TODAY));

        // Hari ini sendiri, dan proyek tanpa jadwal, tetap diterima.
        assertDoesNotThrow(() -> TimeTrackingService.validateEntryDate(TODAY, null, null, TODAY));
    }
}
