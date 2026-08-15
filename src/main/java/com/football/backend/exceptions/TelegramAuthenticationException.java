package com.football.backend.exceptions;

public class TelegramAuthenticationException extends RuntimeException {
    public TelegramAuthenticationException(String message) {
        super(message);
    }
}
