package com.example.aichat.service;

import com.example.aichat.model.entity.ApiCallLog;
import com.example.aichat.repository.ApiCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);

    private final ApiCallLogRepository logRepository;

    public UsageService(ApiCallLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 异步记录调用日志
     */
    @Async
    public void logCall(String keyId, Long tenantId, String model,
                        int promptTokens, int completionTokens,
                        int durationMs, String status, String errorMsg) {
        try {
            ApiCallLog callLog = new ApiCallLog();
            callLog.setKeyId(keyId);
            callLog.setTenantId(tenantId);
            callLog.setModel(model);
            callLog.setPromptTokens(promptTokens);
            callLog.setCompletionTokens(completionTokens);
            callLog.setTotalTokens(promptTokens + completionTokens);
            callLog.setDurationMs(durationMs);
            callLog.setStatus(status);
            callLog.setErrorMessage(errorMsg);
            logRepository.save(callLog);
            log.debug("[用量] 记录调用日志: keyId={}, model={}, tokens={}, duration={}ms",
                keyId, model, promptTokens + completionTokens, durationMs);
        } catch (Exception e) {
            log.error("[用量] 记录调用日志失败: keyId={}, model={}", keyId, model, e);
        }
    }

    /**
     * 今日汇总
     */
    public Map<String, Object> getTodaySummary(Long tenantId) {
        return logRepository.todaySummary(tenantId, LocalDate.now().atStartOfDay());
    }

    /**
     * 按日统计
     */
    public List<Map<String, Object>> getDailyUsage(Long tenantId, LocalDate startDate, LocalDate endDate) {
        return logRepository.statsByTenantAndDateRange(tenantId,
            startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    /**
     * 按 Key 统计
     */
    public List<Map<String, Object>> getKeyStats(Long tenantId, LocalDate startDate, LocalDate endDate) {
        return logRepository.statsByKey(tenantId,
            startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    /**
     * 按模型统计
     */
    public List<Map<String, Object>> getModelStats(Long tenantId, LocalDate startDate, LocalDate endDate) {
        return logRepository.statsByModel(tenantId,
            startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }
}
