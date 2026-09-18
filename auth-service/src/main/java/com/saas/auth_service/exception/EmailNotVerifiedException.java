package com.saas.auth_service.exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends AppException {
    public EmailNotVerifiedException() {
        super("Please verify your email before logging in", "EMAIL_NOT_VERIFIED", HttpStatus.FORBIDDEN);
    }
}
