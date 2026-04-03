package com.example.aichat.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "monthly_quota")
    private Long monthlyQuota = 1000000L;

    @Column(name = "daily_quota")
    private Long dailyQuota = 100000L;

    @Column(name = "monthly_used")
    private Long monthlyUsed = 0L;

    @Column(name = "daily_used")
    private Long dailyUsed = 0L;

    @Column(name = "quota_reset_day")
    private Integer quotaResetDay = 1;

    @Column(name = "last_daily_reset")
    private LocalDate lastDailyReset;

    @Column(name = "last_monthly_reset")
    private LocalDate lastMonthlyReset;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
