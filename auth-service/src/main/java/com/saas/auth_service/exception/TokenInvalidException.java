package com.saas.auth_service.exception;

import org.springframework.http.HttpStatus;

public class TokenInvalidException extends AppException {
    public TokenInvalidException(String message) {
        super(message, "TOKEN_INVALID", HttpStatus.BAD_REQUEST);
    }
}
