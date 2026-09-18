package com.saas.auth_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mengunci kontrak respons error.
 *
 * <p>Test ini ada karena bug yang dulu benar-benar terjadi: catch-all
 * {@code @ExceptionHandler(Exception.class)} juga menangkap ResponseStatusException —
 * yang merupakan turunan RuntimeException — dan karena handler di
 * {@code @RestControllerAdvice} menang atas resolver bawaan Spring, <b>setiap</b>
 * 400/401/403/404 yang sengaja dilempar keluar sebagai 500, lengkap dengan pesan
 * internal yang bocor di details[].
 *
 * <p>Cacat itu tidak terlihat dari membaca kode: berkasnya ada, handlernya ada,
 * semuanya tampak benar. Yang menangkapnya adalah menembak endpoint sungguhan.
 * Test ini membuat pemeriksaan itu otomatis.
 *
 * <p>Standalone setup, bukan {@code @SpringBootTest}: ini menguji handler-nya, bukan
 * kabel Spring-nya, jadi tidak perlu database, Redis, atau RabbitMQ — dan karena itu
 * bisa berjalan di CI tanpa infrastruktur apa pun.
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/boom/not-found")
        String notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyek tidak ditemukan.");
        }

        @GetMapping("/boom/forbidden")
        String forbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak punya akses.");
        }

        @GetMapping("/boom/illegal-argument")
        String illegalArgument() {
            throw new IllegalArgumentException("Nilai tidak valid.");
        }

        @GetMapping("/boom/conflict")
        String conflict() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\"");
        }

        @GetMapping("/boom/no-route")
        String noRoute() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "api/v1/tidak-ada", "/api/v1/tidak-ada");
        }

        @GetMapping("/boom/wrong-content-type")
        String wrongContentType() throws HttpMediaTypeNotSupportedException {
            throw new HttpMediaTypeNotSupportedException(
                    MediaType.APPLICATION_JSON, java.util.List.of(MediaType.MULTIPART_FORM_DATA));
        }

        @GetMapping("/boom/unexpected")
        String unexpected() {
            throw new IllegalStateException("koneksi ke 10.0.0.7:5432 gagal saat SELECT password_hash");
        }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void statusYangSengajaDilemparTidakDitulisUlangJadi500() throws Exception {
        mvc.perform(get("/boom/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Proyek tidak ditemukan."));

        mvc.perform(get("/boom/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("Anda tidak punya akses."));
    }

    @Test
    void argumenTidakValidAdalah400BukanEmpatNolEmpat() throws Exception {
        // Pemetaan lama mengirimnya sebagai 404 NOT_FOUND, yang membuat password
        // melewati batas 72-byte BCrypt muncul sebagai "tidak ditemukan".
        mvc.perform(get("/boom/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void pelanggaranUniqueAdalahKonflik() throws Exception {
        mvc.perform(get("/boom/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                // Pesan driver memuat nama constraint dan kadang nilai kolom; tidak
                // boleh diteruskan ke klien.
                .andExpect(jsonPath("$.error.message").value("Data bentrok dengan data yang sudah ada."));
    }

    @Test
    void kegagalanTakTerdugaTidakMembocorkanDetailInternal() throws Exception {
        mvc.perform(get("/boom/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                // Host internal, port, dan nama kolom dari pesan exception tidak boleh
                // sampai ke klien. requestId yang menghubungkannya ke log.
                .andExpect(jsonPath("$.error.message").value(
                        "Terjadi kesalahan tak terduga. Sertakan requestId saat melapor."))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void urlTidakDikenalAdalah404BukanLimaRatus() throws Exception {
        // Tanpa handler khusus, NoResourceFoundException jatuh ke catch-all dan setiap
        // salah ketik URL dijawab 500 plus stack trace penuh di log.
        mvc.perform(get("/boom/no-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                // Path yang diminta tidak dipantulkan kembali ke respons.
                .andExpect(jsonPath("$.error.message").value("Endpoint tidak ditemukan."));
    }

    @Test
    void contentTypeSalahAdalah415BukanLimaRatus() throws Exception {
        // Unggahan gambar pernah terkirim sebagai application/json karena axios dipaksa
        // memakai header itu. Server menjawab 500, jadi pengguna diberi tahu server
        // rusak padahal yang salah tipe kontennya.
        mvc.perform(get("/boom/wrong-content-type"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void setiapResponsErrorMemakaiBentukYangSama() throws Exception {
        // Frontend punya satu pembaca error (services/apiError.ts). Kontrak ini yang
        // membuatnya cukup satu.
        for (String path : new String[] { "/boom/not-found", "/boom/illegal-argument",
                "/boom/conflict", "/boom/no-route", "/boom/wrong-content-type", "/boom/unexpected" }) {
            mvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error.code").isNotEmpty())
                    .andExpect(jsonPath("$.error.message").isNotEmpty())
                    .andExpect(jsonPath("$.error.details").isArray())
                    .andExpect(jsonPath("$.error.requestId").isNotEmpty())
                    .andExpect(jsonPath("$.error.timestamp").isNotEmpty());
        }
    }
}
