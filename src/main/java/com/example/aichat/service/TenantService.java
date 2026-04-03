package com.example.aichat.service;

import com.example.aichat.model.dto.TenantRequest;
import com.example.aichat.model.entity.Tenant;
import com.example.aichat.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant create(TenantRequest request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setContactEmail(request.contactEmail());
        if (request.monthlyQuota() != null) tenant.setMonthlyQuota(request.monthlyQuota());
        if (request.dailyQuota() != null) tenant.setDailyQuota(request.dailyQuota());
        tenant = tenantRepository.save(tenant);
        log.info("[租户] 创建租户: id={}, name={}", tenant.getId(), tenant.getName());
        return tenant;
    }

    public List<Tenant> listAll() {
        return tenantRepository.findAll();
    }

    public Tenant getById(Long id) {
        return tenantRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("租户不存在: " + id));
    }

    public Tenant update(Long id, TenantRequest request) {
        Tenant tenant = getById(id);
        if (request.name() != null) tenant.setName(request.name());
        if (request.contactEmail() != null) tenant.setContactEmail(request.contactEmail());
        return tenantRepository.save(tenant);
    }

    public void updateQuota(Long id, Long monthlyQuota, Long dailyQuota) {
        Tenant tenant = getById(id);
        tenant.setMonthlyQuota(monthlyQuota);
        tenant.setDailyQuota(dailyQuota);
        tenantRepository.save(tenant);
        log.info("[租户] 更新配额: id={}, monthly={}, daily={}", id, monthlyQuota, dailyQuota);
    }
}
