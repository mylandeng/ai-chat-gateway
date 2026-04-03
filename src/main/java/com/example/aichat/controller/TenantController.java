package com.example.aichat.controller;

import com.example.aichat.model.dto.QuotaInfo;
import com.example.aichat.model.dto.TenantRequest;
import com.example.aichat.model.entity.Tenant;
import com.example.aichat.service.QuotaService;
import com.example.aichat.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private static final Logger log = LoggerFactory.getLogger(TenantController.class);

    private final TenantService tenantService;
    private final QuotaService quotaService;

    public TenantController(TenantService tenantService, QuotaService quotaService) {
        this.tenantService = tenantService;
        this.quotaService = quotaService;
    }

    @PostMapping
    public Tenant create(@RequestBody TenantRequest request) {
        return tenantService.create(request);
    }

    @GetMapping
    public List<Tenant> list() {
        return tenantService.listAll();
    }

    @GetMapping("/{id}")
    public Tenant get(@PathVariable Long id) {
        return tenantService.getById(id);
    }

    @PutMapping("/{id}")
    public Tenant update(@PathVariable Long id, @RequestBody TenantRequest request) {
        return tenantService.update(id, request);
    }

    @GetMapping("/{id}/quota")
    public QuotaInfo getQuota(@PathVariable Long id) {
        return quotaService.getQuotaInfo(id);
    }

    @PutMapping("/{id}/quota")
    public void updateQuota(@PathVariable Long id,
                           @RequestParam Long monthlyQuota,
                           @RequestParam Long dailyQuota) {
        tenantService.updateQuota(id, monthlyQuota, dailyQuota);
    }
}
