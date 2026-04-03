package com.example.aichat.service;

import com.example.aichat.exception.ApiKeyExpiredException;
import com.example.aichat.exception.ApiKeyInvalidException;
import com.example.aichat.model.entity.ApiKey;
import com.example.aichat.model.vo.ApiKeyVO;
import com.example.aichat.repository.ApiKeyRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * 创建 API Key
     * @return 完整的明文 Key（只返回这一次！）
     */
    public ApiKeyCreateResult createKey(Long tenantId, String name) {
        String rawKey = "sk-" + generateRandomString(48);
        String prefix = rawKey.substring(0, 7);
        String hashed = encoder.encode(rawKey);
        String keyId = "ak-" + generateRandomString(24);

        ApiKey apiKey = new ApiKey();
        apiKey.setKeyId(keyId);
        apiKey.setKeySecret(hashed);
        apiKey.setKeyPrefix(prefix);
        apiKey.setName(name);
        apiKey.setTenantId(tenantId);
        apiKey.setRateLimit(60);
        apiKeyRepository.save(apiKey);

        return new ApiKeyCreateResult(keyId, rawKey, prefix + "***");
    }

    /**
     * 验证 API Key
     */
    public ApiKey validateKey(String rawKey) {
        if (rawKey == null || rawKey.length() < 7) {
            throw new ApiKeyInvalidException("无效的 API Key");
        }

        String prefix = rawKey.substring(0, 7);
        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefixAndStatus(prefix, 1);

        for (ApiKey key : candidates) {
            if (encoder.matches(rawKey, key.getKeySecret())) {
                if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
                    throw new ApiKeyExpiredException("API Key 已过期");
                }
                key.setLastUsedAt(LocalDateTime.now());
                apiKeyRepository.save(key);
                return key;
            }
        }
        throw new ApiKeyInvalidException("无效的 API Key");
    }

    public List<ApiKeyVO> listByTenant(Long tenantId) {
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .map(k -> new ApiKeyVO(
                k.getKeyId(), k.getKeyPrefix() + "***", k.getName(),
                k.getStatus(), k.getRateLimit(), k.getAllowedModels(),
                k.getExpiresAt(), k.getLastUsedAt(), k.getCreatedAt()))
            .toList();
    }

    public void updateStatus(String keyId, int status) {
        apiKeyRepository.findByKeyId(keyId).ifPresent(key -> {
            key.setStatus(status);
            apiKeyRepository.save(key);
        });
    }

    public void deleteKey(String keyId) {
        apiKeyRepository.findByKeyId(keyId).ifPresent(apiKeyRepository::delete);
    }

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) {
            sb.append(UUID.randomUUID().toString().replace("-", ""));
        }
        return sb.substring(0, length);
    }

    public record ApiKeyCreateResult(String keyId, String key, String displayKey) {}
}
