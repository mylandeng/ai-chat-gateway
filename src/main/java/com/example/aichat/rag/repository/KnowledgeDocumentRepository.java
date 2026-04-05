package com.example.aichat.rag.repository;

import com.example.aichat.rag.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<KnowledgeDocument> findByKbIdOrderByCreatedAtDesc(Long kbId);

    int countByKbId(Long kbId);

    void deleteByKbId(Long kbId);
}
