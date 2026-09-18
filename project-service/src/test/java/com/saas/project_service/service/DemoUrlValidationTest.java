package com.saas.project_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * demoUrl masuk ke src sebuah iframe. Skemanya dulu hanya dicek di frontend
 * (safeDemoUrl), jadi klien lain — mobile, integrasi, curl — melewati pengecekan itu
 * sepenuhnya. Panjangnya tidak pernah dicek di mana pun terhadap VARCHAR(1000).
 */
class DemoUrlValidationTest {

    @Test
    void acceptsHttpAndHttpsAndTrims() {
        assertEquals("https://demo.example.com", ProjectService.validateDemoUrl("  https://demo.example.com  "));
        assertEquals("http://localhost:5173", ProjectService.validateDemoUrl("http://localhost:5173"));
    }

    @Test
    void blankBecomesNull() {
        assertNull(ProjectService.validateDemoUrl(null));
        assertNull(ProjectService.validateDemoUrl("   "));
    }

    @Test
    void rejectsScriptSchemes() {
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateDemoUrl("javascript:alert(1)"));
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateDemoUrl("data:text/html;base64,PHN2Zz4="));
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateDemoUrl("//evil.example.com"));
    }

    @Test
    void boundedToItsColumnWidth() {
        String tooLong = "https://x.example.com/" + "y".repeat(ProjectService.MAX_DEMO_URL_LENGTH);
        assertThrows(ResponseStatusException.class, () -> ProjectService.validateDemoUrl(tooLong));
    }
}
