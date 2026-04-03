package com.example.aichat.repository;

import com.example.aichat.model.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByKeyPrefixAndStatus(String keyPrefix, Integer status);

    List<ApiKey> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<ApiKey> findByKeyId(String keyId);
}
