package com.example.aichat.rag.repository;

import com.example.aichat.rag.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<KnowledgeDocument> findByKbIdOrderByCreatedAtDesc(Long kbId);

    int countByKbId(Long kbId);

    boolean existsByIdAndKbId(Long id, Long kbId);

    Optional<KnowledgeDocument> findByIdAndKbId(Long id, Long kbId);

    void deleteByKbId(Long kbId);
}
