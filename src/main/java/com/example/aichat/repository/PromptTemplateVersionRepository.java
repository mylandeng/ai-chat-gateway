package com.example.aichat.repository;

import com.example.aichat.model.entity.PromptTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromptTemplateVersionRepository extends JpaRepository<PromptTemplateVersion, Long> {

    List<PromptTemplateVersion> findByTemplateIdOrderByVersionDesc(Long templateId);
}
