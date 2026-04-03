package com.example.aichat.model.dto;

public record QuotaInfo(
    long dailyQuota,
    long dailyUsed,
    long monthlyQuota,
    long monthlyUsed,
    double dailyUsagePercent,
    double monthlyUsagePercent
) {
    public QuotaInfo(long dailyQuota, long dailyUsed, long monthlyQuota, long monthlyUsed) {
        this(dailyQuota, dailyUsed, monthlyQuota, monthlyUsed,
            dailyQuota > 0 ? (double) dailyUsed / dailyQuota * 100 : 0,
            monthlyQuota > 0 ? (double) monthlyUsed / monthlyQuota * 100 : 0);
    }
}
