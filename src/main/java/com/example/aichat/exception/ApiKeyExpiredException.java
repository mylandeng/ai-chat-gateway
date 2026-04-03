package com.example.aichat.exception;

public class ApiKeyExpiredException extends RuntimeException {
    public ApiKeyExpiredException(String message) {
        super(message);
    }
}
