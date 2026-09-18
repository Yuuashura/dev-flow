package com.saas.auth_service.exception;

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
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        ErrorResponse body = buildErrorResponse(ex.getErrorCode(), ex.getMessage(), null, requestId);
        return new ResponseEntity<>(body, ex.getHttpStatus());
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
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        // Pesan sengaja tidak memuat path yang diminta: itu memantulkan input pemanggil
        // kembali ke responsnya.
        ErrorResponse body = buildErrorResponse(
                HttpStatus.NOT_FOUND.name(), "Endpoint tidak ditemukan.", null, getRequestId(request));
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
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
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        ErrorResponse body = buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE.name(),
                "Tipe konten tidak didukung untuk endpoint ini.", null, getRequestId(request));
        return new ResponseEntity<>(body, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        // message dulu berisi literal "Invalid request parameters" dan alasan sebenarnya
        // hanya ada di details[]. Setiap pembaca error di frontend cuma membaca message,
        // jadi alasan field-level tidak pernah sampai ke user. Sekarang alasan pertama
        // ikut di message; details tetap lengkap untuk yang mau menampilkan semuanya.
        String message = details.isEmpty()
                ? "Parameter permintaan tidak valid."
                : String.join("; ", details);
        ErrorResponse body = buildErrorResponse("VALIDATION_ERROR", message, details, getRequestId(request));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /** Tanpa handler ini, catch-all Exception di bawah ikut menangkap
     *  ResponseStatusException (turunan RuntimeException) dan menulis ulang setiap
     *  400/401/403/404 yang sengaja dilempar menjadi 500. Handler yang lebih spesifik
     *  menang, jadi status dan pesan aslinya sampai ke klien. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        ErrorResponse body = buildErrorResponse(status.name(), message, null, getRequestId(request));
        return new ResponseEntity<>(body, status);
    }

    /** Bean validation pada @RequestParam/@PathVariable (@Validated di controller)
     *  melempar ini, bukan MethodArgumentNotValidException. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        ErrorResponse body = buildErrorResponse("VALIDATION_ERROR", "Parameter permintaan tidak valid.", details, getRequestId(request));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /** Body tidak bisa di-parse, atau tipe parameter salah. Keduanya kesalahan klien. */
    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        ErrorResponse body = buildErrorResponse("BAD_REQUEST", "Format permintaan tidak valid.", null, getRequestId(request));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /** Pelanggaran UNIQUE / FK adalah konflik state, bukan kesalahan server. Pesan driver
     *  tidak diteruskan: isinya nama constraint dan kadang nilai kolom. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        log.warn("Data integrity violation [requestId={}]: {}", requestId, ex.getMostSpecificCause().getMessage());
        ErrorResponse body = buildErrorResponse("CONFLICT", "Data bentrok dengan data yang sudah ada.", null, requestId);
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    /** Argumen tidak valid adalah kesalahan klien (400), bukan 404. Pemetaan lama
     *  membuat password yang melewati batas 72-byte BCrypt muncul sebagai
     *  "NOT_FOUND" berbahasa Inggris. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        ErrorResponse body = buildErrorResponse("BAD_REQUEST", ex.getMessage(), null, requestId);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        log.error("Unhandled exception [requestId={}] {}: {}", requestId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        // Pesan exception tidak diteruskan: untuk kegagalan yang benar-benar tak terduga
        // isinya bisa memuat query, nama kolom, atau host internal. requestId yang
        // menghubungkannya ke log.
        ErrorResponse body = buildErrorResponse("INTERNAL_SERVER_ERROR",
                "Terjadi kesalahan tak terduga. Sertakan requestId saat melapor.", null, requestId);
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ErrorResponse buildErrorResponse(String code, String message, List<String> details, String requestId) {
        return ErrorResponse.builder()
                .error(ErrorResponse.ErrorBody.builder()
                        .code(code)
                        .message(message)
                        .details(details != null ? details : List.of())
                        .requestId(requestId)
                        .timestamp(Instant.now())
                        .build())
                .build();
    }

    private String getRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-ID");
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}
