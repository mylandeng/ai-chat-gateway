package com.example.aichat.rag.repository;

import com.example.aichat.rag.model.RagChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagChatSessionRepository extends JpaRepository<RagChatSession, Long> {

    List<RagChatSession> findByKbIdAndTenantIdOrderByUpdatedAtDesc(Long kbId, Long tenantId);

    void deleteByKbId(Long kbId);
}
