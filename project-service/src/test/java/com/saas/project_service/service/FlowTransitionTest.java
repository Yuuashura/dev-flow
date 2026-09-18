package com.saas.project_service.service;

import com.saas.project_service.entity.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Urutan tahap Flow.
 *
 * <p>Aturan ini dulu hanya hidup di frontend: {@code nextStageOf()} di
 * ProjectDetailPage.tsx memaksa TODO -> IN_PROGRESS -> IN_REVIEW -> DONE, sementara
 * endpoint menerima status apa pun. Satu panggilan API langsung bisa memindahkan Flow
 * dari TODO ke DONE, melewati review, dan menaikkan progres proyek ke 100%.
 *
 * <p>Test memanggil {@code ProjectService.validateStageTransition} yang sebenarnya,
 * bukan salinan rumusnya — supaya perubahan pada aturan aslinya ketahuan di sini.
 */
class FlowTransitionTest {

    private static ResponseStatusException reject(TaskStatus from, TaskStatus to) {
        return assertThrows(ResponseStatusException.class,
                () -> ProjectService.validateStageTransition(from, to));
    }

    @Test
    void tahapTidakBolehDilompati() {
        ResponseStatusException e = reject(TaskStatus.TODO, TaskStatus.DONE);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        // Pesannya menyebut tahap sah berikutnya, supaya user tahu harus ke mana.
        assertEquals(true, e.getReason().contains("IN_PROGRESS"));

        reject(TaskStatus.TODO, TaskStatus.IN_REVIEW);
        reject(TaskStatus.IN_PROGRESS, TaskStatus.DONE);
    }

    @Test
    void majuSatuTahapDiterima() {
        assertDoesNotThrow(() -> ProjectService.validateStageTransition(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        assertDoesNotThrow(() -> ProjectService.validateStageTransition(TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW));
        assertDoesNotThrow(() -> ProjectService.validateStageTransition(TaskStatus.IN_REVIEW, TaskStatus.DONE));
    }

    @Test
    void mundurDiizinkanKarenaHasilReviewBisaDitolak() {
        // Tanpa ini, Flow yang gagal review terkunci di IN_REVIEW selamanya — tidak ada
        // satu pun kontrol di UI yang bisa mengembalikannya.
        assertDoesNotThrow(() -> ProjectService.validateStageTransition(TaskStatus.IN_REVIEW, TaskStatus.IN_PROGRESS));
        assertDoesNotThrow(() -> ProjectService.validateStageTransition(TaskStatus.DONE, TaskStatus.TODO));
    }

    @Test
    void tahapYangSamaDitolakBukanNoOpDiam() {
        // Diam-diam menerimanya berarti menulis komentar alasan untuk perpindahan yang
        // tidak pernah terjadi.
        assertEquals(HttpStatus.BAD_REQUEST, reject(TaskStatus.DONE, TaskStatus.DONE).getStatusCode());
    }
}
