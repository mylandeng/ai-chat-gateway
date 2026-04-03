package com.example.aichat.controller;

import com.example.aichat.model.vo.ApiKeyVO;
import com.example.aichat.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/keys")
public class ApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyController.class);

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ApiKeyService.ApiKeyCreateResult create(
            @RequestParam Long tenantId,
            @RequestParam(required = false) String name) {
        log.info("[创建Key] tenantId={}, name={}", tenantId, name);
        return apiKeyService.createKey(tenantId, name);
    }

    @GetMapping
    public List<ApiKeyVO> list(@RequestParam Long tenantId) {
        log.debug("[查询Key] tenantId={}", tenantId);
        return apiKeyService.listByTenant(tenantId);
    }

    @PutMapping("/{keyId}/disable")
    public void disable(@PathVariable String keyId) {
        log.info("[禁用Key] keyId={}", keyId);
        apiKeyService.updateStatus(keyId, 0);
    }

    @PutMapping("/{keyId}/enable")
    public void enable(@PathVariable String keyId) {
        log.info("[启用Key] keyId={}", keyId);
        apiKeyService.updateStatus(keyId, 1);
    }

    @DeleteMapping("/{keyId}")
    public void delete(@PathVariable String keyId) {
        log.warn("[删除Key] keyId={}", keyId);
        apiKeyService.deleteKey(keyId);
    }
}
