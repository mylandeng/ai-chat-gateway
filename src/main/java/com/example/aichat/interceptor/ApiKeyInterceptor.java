package com.example.aichat.interceptor;

import com.example.aichat.context.RequestContext;
import com.example.aichat.exception.ApiKeyExpiredException;
import com.example.aichat.exception.ApiKeyInvalidException;
import com.example.aichat.model.entity.ApiKey;
import com.example.aichat.service.ApiKeyService;
import com.example.aichat.service.QuotaService;
import com.example.aichat.service.RateLimitManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyInterceptor.class);

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private RateLimitManager rateLimitManager;

    @Autowired
    private QuotaService quotaService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) throws Exception {

        String method = request.getMethod();
        String uri = request.getRequestURI();
        log.debug("[拦截器] {} {} 开始鉴权", method, uri);

        // 提取 API Key
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[拦截器] {} {} 缺少Authorization header", method, uri);
            writeError(response, 401, "缺少 Authorization header");
            return false;
        }
        String rawKey = authHeader.substring(7);
        String keyPrefix = rawKey.length() >= 7 ? rawKey.substring(0, 7) : "???";

        // 验证 Key
        ApiKey apiKey;
        try {
            apiKey = apiKeyService.validateKey(rawKey);
        } catch (ApiKeyInvalidException e) {
            log.warn("[拦截器] {} {} Key验证失败, prefix={}", method, uri, keyPrefix);
            writeError(response, 401, "无效的 API Key");
            return false;
        } catch (ApiKeyExpiredException e) {
            log.warn("[拦截器] {} {} Key已过期, prefix={}", method, uri, keyPrefix);
            writeError(response, 401, "API Key 已过期");
            return false;
        }

        // 限流检查
        if (!rateLimitManager.tryConsume(apiKey.getKeyId(), apiKey.getRateLimit())) {
            log.warn("[拦截器] {} {} 触发限流, keyId={}, rateLimit={}/min", method, uri, apiKey.getKeyId(), apiKey.getRateLimit());
            writeError(response, 429, "请求过于频繁，请稍后重试");
            return false;
        }

        // 配额检查
        if (!quotaService.checkQuota(apiKey.getTenantId())) {
            log.warn("[拦截器] {} {} Token配额超限, keyId={}, tenantId={}", method, uri, apiKey.getKeyId(), apiKey.getTenantId());
            writeError(response, 429, "Token 配额已用尽");
            return false;
        }

        // 将租户信息放入请求上下文
        request.setAttribute("tenantId", apiKey.getTenantId());
        request.setAttribute("keyId", apiKey.getKeyId());

        // 同时放入 ThreadLocal（供 Service 层使用）
        RequestContext.set("tenantId", apiKey.getTenantId());
        RequestContext.set("keyId", apiKey.getKeyId());

        log.debug("[拦截器] {} {} 鉴权通过, keyId={}, tenantId={}", method, uri, apiKey.getKeyId(), apiKey.getTenantId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        RequestContext.clear();
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            "{\"error\":{\"message\":\"" + message + "\",\"code\":" + status + "}}");
    }
}
