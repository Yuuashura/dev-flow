package com.saas.workspace_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service ini sebelumnya tidak punya @RestControllerAdvice sama sekali, jadi setiap
 * kegagalan — termasuk penolakan izin dan slug yang sudah dipakai — keluar sebagai 500
 * dengan stack trace. Bentuk respons sengaja dibuat identik dengan auth-service supaya
 * frontend cuma perlu satu pembaca error:
 *
 * <pre>{ "error": { code, message, details[], requestId, timestamp } }</pre>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Harus lebih spesifik daripada catch-all di bawah, kalau tidak setiap status yang
     *  sengaja dilempar ditulis ulang jadi 500. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return new ResponseEntity<>(body(status.name(), message, null, requestId(request)), status);
    }

    /**
     * URL yang tidak cocok dengan controller mana pun adalah 404, bukan 500.
     *
     * <p>Tanpa handler ini, NoResourceFoundException jatuh ke catch-all
     * {@code @ExceptionHandler(Exception.class)} di bawah — jadi setiap salah ketik URL,
     * setiap endpoint yang sudah dihapus, dan setiap pemindai yang menembak jalur acak
     * dijawab "Terjadi kesalahan tak terduga" plus satu stack trace penuh di level ERROR.
     * Log jadi penuh kegagalan yang bukan kegagalan, dan klien diberi tahu server rusak
     * padahal yang salah adalah alamatnya.
     *
     * <p>Ditemukan saat memverifikasi penghapusan endpoint revisi: rute yang hilang
     * membalas 500, bukan 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        // Pesan sengaja tidak memuat path yang diminta: itu memantulkan input pemanggil
        // kembali ke responsnya.
        return new ResponseEntity<>(
                body(HttpStatus.NOT_FOUND.name(), "Endpoint tidak ditemukan.", null, requestId(request)),
                HttpStatus.NOT_FOUND);
    }

    /**
     * Content-Type yang tidak cocok adalah 415, bukan 500.
     *
     * <p>Tanpa handler ini HttpMediaTypeNotSupportedException jatuh ke catch-all di
     * bawah dan keluar sebagai "Terjadi kesalahan tak terduga" — persis yang terjadi
     * saat unggahan gambar terkirim sebagai application/json: pengguna diberi tahu
     * server rusak, padahal yang salah adalah tipe kontennya, dan log terisi stack
     * trace untuk kesalahan klien.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        Map<String, Object> body = body(HttpStatus.UNSUPPORTED_MEDIA_TYPE.name(),
                "Tipe konten tidak didukung untuk endpoint ini.", null, requestId(request));
        return new ResponseEntity<>(body, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        String message = details.isEmpty() ? "Parameter permintaan tidak valid." : String.join("; ", details);
        return new ResponseEntity<>(body("VALIDATION_ERROR", message, details, requestId(request)), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        String message = details.isEmpty() ? "Parameter permintaan tidak valid." : String.join("; ", details);
        return new ResponseEntity<>(body("VALIDATION_ERROR", message, details, requestId(request)), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class })
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return new ResponseEntity<>(body("BAD_REQUEST", "Format permintaan tidak valid.", null, requestId(request)),
                HttpStatus.BAD_REQUEST);
    }

    /** Pelanggaran UNIQUE / FK adalah konflik state, bukan kesalahan server. Pesan driver
     *  tidak diteruskan: isinya nama constraint dan kadang nilai kolom. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("Data integrity violation [requestId={}]: {}", requestId, ex.getMostSpecificCause().getMessage());
        return new ResponseEntity<>(body("CONFLICT", "Data bentrok dengan data yang sudah ada.", null, requestId),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return new ResponseEntity<>(body("BAD_REQUEST", ex.getMessage(), null, requestId(request)), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Unhandled exception [requestId={}] {}: {}", requestId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        // Pesan exception tidak diteruskan: untuk kegagalan tak terduga isinya bisa memuat
        // query, nama kolom, atau host internal. requestId yang menghubungkannya ke log.
        return new ResponseEntity<>(body("INTERNAL_SERVER_ERROR",
                "Terjadi kesalahan tak terduga. Sertakan requestId saat melapor.", null, requestId),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Map<String, Object> body(String code, String message, List<String> details, String requestId) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message != null ? message : "");
        error.put("details", details != null ? details : List.of());
        error.put("requestId", requestId);
        error.put("timestamp", Instant.now().toString());
        return Map.of("error", error);
    }

    private String requestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-ID");
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}
