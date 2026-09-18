package com.saas.auth_service.service;

import com.saas.auth_service.exception.AppException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Helper ini dipakai jalur register DAN ganti password. Sebelumnya tidak ada batas
 * sama sekali: BCrypt melempar IllegalArgumentException di atas 72 byte, dan pemetaan
 * exception lama mengubahnya jadi 404 berbahasa Inggris.
 */
class CredentialValidationTest {

    @Test
    void passwordIsBoundedByBcryptInputLimit() {
        assertDoesNotThrow(() -> AuthService.validatePassword("rahasia123"));

        // Tepat di batas masih boleh, satu byte lewat ditolak.
        String at72 = "a".repeat(AuthService.MAX_PASSWORD_BYTES);
        assertDoesNotThrow(() -> AuthService.validatePassword(at72));
        assertThrows(AppException.class,
                () -> AuthService.validatePassword("a".repeat(AuthService.MAX_PASSWORD_BYTES + 1)));
    }

    @Test
    void passwordLimitCountsBytesNotCharacters() {
        // Satu emoji = 4 byte UTF-8. 20 emoji = 80 byte, tapi hanya 40 char di Java,
        // jadi pengecekan berbasis length() akan meloloskannya dan BCrypt yang meledak.
        String emoji = "😀".repeat(20);
        assertEquals(80, emoji.getBytes(StandardCharsets.UTF_8).length);
        assertThrows(AppException.class, () -> AuthService.validatePassword(emoji));
    }

    @Test
    void passwordRejectsShortAndMissing() {
        assertThrows(AppException.class, () -> AuthService.validatePassword(null));
        assertThrows(AppException.class, () -> AuthService.validatePassword(""));
        assertThrows(AppException.class, () -> AuthService.validatePassword("a".repeat(AuthService.MIN_PASSWORD_LENGTH - 1)));
    }

    @Test
    void emailIsNormalisedAndBoundedToItsColumn() {
        assertEquals("user@example.com", AuthService.validateEmail("  User@Example.COM  "));
        assertThrows(AppException.class, () -> AuthService.validateEmail(null));
        assertThrows(AppException.class, () -> AuthService.validateEmail("   "));

        // @Email sendiri menerima sampai 320 karakter (batas RFC), sedangkan kolomnya
        // varchar(255) — celah itu yang ditutup di sini.
        String longLocal = "a".repeat(250) + "@example.com";
        assertThrows(AppException.class, () -> AuthService.validateEmail(longLocal));
    }
}
