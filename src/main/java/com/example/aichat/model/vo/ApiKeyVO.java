package com.example.aichat.model.vo;

import java.time.LocalDateTime;

public record ApiKeyVO(
    String keyId,
    String displayKey,
    String name,
    Integer status,
    Integer rateLimit,
    String allowedModels,
    LocalDateTime expiresAt,
    LocalDateTime lastUsedAt,
    LocalDateTime createdAt
) {}
