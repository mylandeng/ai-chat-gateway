package com.example.aichat.rag.repository;

import com.example.aichat.rag.model.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<KnowledgeBase> findByShareToken(String shareToken);
}
