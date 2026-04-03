package com.example.aichat.model.dto;

public record ChatResponse(
    String reply,
    String model,
    long tokenUsed
) {}
