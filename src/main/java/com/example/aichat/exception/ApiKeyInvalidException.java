package com.example.aichat.exception;

public class ApiKeyInvalidException extends RuntimeException {
    public ApiKeyInvalidException(String message) {
        super(message);
    }
}
