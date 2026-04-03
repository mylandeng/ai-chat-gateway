package com.example.aichat.service;

import com.example.aichat.model.dto.QuotaInfo;
import com.example.aichat.model.entity.Tenant;
import com.example.aichat.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final TenantRepository tenantRepository;

    public QuotaService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * 检查配额是否足够
     */
    public boolean checkQuota(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            log.warn("[配额] 租户不存在: {}", tenantId);
            return true; // 租户不存在时放行（兼容 W1 没有租户表的情况）
        }

        resetQuotaIfNeeded(tenant);

        if (tenant.getDailyUsed() >= tenant.getDailyQuota()) {
            log.warn("[配额] 日配额超限, tenantId={}, used={}, quota={}",
                tenantId, tenant.getDailyUsed(), tenant.getDailyQuota());
            return false;
        }
        if (tenant.getMonthlyUsed() >= tenant.getMonthlyQuota()) {
            log.warn("[配额] 月配额超限, tenantId={}, used={}, quota={}",
                tenantId, tenant.getMonthlyUsed(), tenant.getMonthlyQuota());
            return false;
        }

        checkWarning(tenant);
        return true;
    }

    /**
     * 消费配额
     */
    public void consumeQuota(Long tenantId, int tokenCount) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.setDailyUsed(tenant.getDailyUsed() + tokenCount);
            tenant.setMonthlyUsed(tenant.getMonthlyUsed() + tokenCount);
            tenantRepository.save(tenant);
            log.debug("[配额] 扣减 tenantId={}, tokens={}, 日已用={}, 月已用={}",
                tenantId, tokenCount, tenant.getDailyUsed(), tenant.getMonthlyUsed());
        });
    }

    /**
     * 获取配额信息
     */
    public QuotaInfo getQuotaInfo(Long tenantId) {
        Tenant t = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("租户不存在: " + tenantId));
        resetQuotaIfNeeded(t);
        return new QuotaInfo(t.getDailyQuota(), t.getDailyUsed(),
            t.getMonthlyQuota(), t.getMonthlyUsed());
    }

    private void resetQuotaIfNeeded(Tenant tenant) {
        LocalDate today = LocalDate.now();
        boolean changed = false;

        // 日配额重置
        if (tenant.getLastDailyReset() == null || tenant.getLastDailyReset().isBefore(today)) {
            tenant.setDailyUsed(0L);
            tenant.setLastDailyReset(today);
            changed = true;
        }

        // 月配额重置
        if (tenant.getLastMonthlyReset() == null ||
            (today.getDayOfMonth() == tenant.getQuotaResetDay() &&
             !today.equals(tenant.getLastMonthlyReset()))) {
            tenant.setMonthlyUsed(0L);
            tenant.setLastMonthlyReset(today);
            changed = true;
        }

        if (changed) {
            tenantRepository.save(tenant);
        }
    }

    private void checkWarning(Tenant tenant) {
        double dailyRatio = (double) tenant.getDailyUsed() / tenant.getDailyQuota();
        double monthlyRatio = (double) tenant.getMonthlyUsed() / tenant.getMonthlyQuota();

        if (dailyRatio > 0.8) {
            log.warn("[配额预警] 租户={} 日配额使用 {}%", tenant.getName(), (int)(dailyRatio * 100));
        }
        if (monthlyRatio > 0.8) {
            log.warn("[配额预警] 租户={} 月配额使用 {}%", tenant.getName(), (int)(monthlyRatio * 100));
        }
    }
}
