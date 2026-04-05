package com.example.aichat.rag.repository;

import com.example.aichat.rag.model.RagChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RagChatMessageRepository extends JpaRepository<RagChatMessage, Long> {

    List<RagChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
