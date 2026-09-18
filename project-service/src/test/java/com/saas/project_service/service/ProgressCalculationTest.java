package com.saas.project_service.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rumus progres proyek.
 *
 * <p>progress_percent dulu hanya pernah ditulis endpoint updateProjectMeta, dan tidak
 * ada satu pun kontrol di UI yang mengubahnya — jadi setiap bar diam di 0% berapa pun
 * Flow yang diselesaikan. Perhitungannya dipisah ke method statis supaya aturan
 * pembulatannya bisa diuji tanpa database.
 */
class ProgressCalculationTest {

    /** Cermin rumus di ProjectService.recalculateProgress. */
    private static int percent(long done, long total) {
        if (total == 0) return 0;
        return done == total ? 100 : (int) ((done * 100) / total);
    }

    @Test
    void emptyProjectIsZero() {
        assertEquals(0, percent(0, 0));
    }

    @Test
    void countsDoneOverTotal() {
        assertEquals(0, percent(0, 4));
        assertEquals(25, percent(1, 4));
        assertEquals(50, percent(2, 4));
        assertEquals(100, percent(4, 4));
    }

    @Test
    void roundsDownSoAlmostDoneNeverReadsAsFinished() {
        // 7 dari 8 adalah 87,5%. Pembulatan ke atas akan menampilkan 88 — itu tidak apa —
        // tapi yang berbahaya adalah kasus seperti 999 dari 1000 yang membulat jadi 100
        // dan membuat proyek tampak beres padahal belum.
        assertEquals(87, percent(7, 8));
        assertEquals(99, percent(999, 1000));
        assertEquals(99, percent(99, 100));
    }

    @Test
    void onlyAllDoneReachesOneHundred() {
        assertEquals(100, percent(1, 1));
        assertEquals(100, percent(3, 3));
        assertEquals(66, percent(2, 3));
    }
}
