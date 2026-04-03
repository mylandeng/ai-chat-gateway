package com.example.aichat.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "api_call_log")
public class ApiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_id", nullable = false, length = 32)
    private String keyId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens")
    private Integer completionTokens = 0;

    @Column(name = "total_tokens")
    private Integer totalTokens = 0;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
