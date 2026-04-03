package com.example.aichat.rag.repository;

import com.example.aichat.rag.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
