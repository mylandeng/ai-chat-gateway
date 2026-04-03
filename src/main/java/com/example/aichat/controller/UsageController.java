package com.example.aichat.controller;

import com.example.aichat.service.UsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private static final Logger log = LoggerFactory.getLogger(UsageController.class);

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/summary")
    public Map<String, Object> todaySummary(HttpServletRequest request) {
        Long tenantId = (Long) request.getAttribute("tenantId");
        log.debug("[用量] 查询今日汇总, tenantId={}", tenantId);
        return usageService.getTodaySummary(tenantId);
    }

    @GetMapping("/daily")
    public List<Map<String, Object>> dailyUsage(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Long tenantId = (Long) request.getAttribute("tenantId");
        log.debug("[用量] 查询日用量, tenantId={}, {}~{}", tenantId, start, end);
        return usageService.getDailyUsage(tenantId, start, end);
    }

    @GetMapping("/by-key")
    public List<Map<String, Object>> byKey(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Long tenantId = (Long) request.getAttribute("tenantId");
        return usageService.getKeyStats(tenantId, start, end);
    }

    @GetMapping("/by-model")
    public List<Map<String, Object>> byModel(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Long tenantId = (Long) request.getAttribute("tenantId");
        return usageService.getModelStats(tenantId, start, end);
    }
}
