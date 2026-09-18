package com.saas.auth_service.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends AppException {
    public EmailAlreadyExistsException(String email) {
        super("Email '" + email + "' already registered", "EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT);
    }
}
