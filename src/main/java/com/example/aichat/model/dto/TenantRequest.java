package com.example.aichat.model.dto;

public record TenantRequest(
    String name,
    String contactEmail,
    Long monthlyQuota,
    Long dailyQuota
) {}
