package com.example.aichat.model.dto;

public record PromptTemplateRequest(
    String name,
    String description,
    String category,
    String content,
    String variables,
    Boolean isPublic,
    String changeNote
) {}
