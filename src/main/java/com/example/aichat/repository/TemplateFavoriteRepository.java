package com.example.aichat.repository;

import com.example.aichat.model.entity.TemplateFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateFavoriteRepository extends JpaRepository<TemplateFavorite, Long> {

    Optional<TemplateFavorite> findByTenantIdAndTemplateId(Long tenantId, Long templateId);

    List<TemplateFavorite> findByTenantId(Long tenantId);

    void deleteByTenantIdAndTemplateId(Long tenantId, Long templateId);
}
