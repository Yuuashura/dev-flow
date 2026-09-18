package com.saas.workspace_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Kolom invitations.email VARCHAR(255). @Email membatasi bentuk, bukan panjang, dan
 * sebelum ini tidak ada jalur undangan yang memangkas atau membatasinya.
 */
class InviteEmailValidationTest {

    @Test
    void inviteEmailIsNormalisedAndBounded() {
        assertEquals("orang@example.com", WorkspaceService.validateInviteEmail("  Orang@Example.COM  "));

        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateInviteEmail(null));
        assertThrows(ResponseStatusException.class, () -> WorkspaceService.validateInviteEmail("   "));
        assertThrows(ResponseStatusException.class,
                () -> WorkspaceService.validateInviteEmail("a".repeat(250) + "@example.com"));
    }
}
