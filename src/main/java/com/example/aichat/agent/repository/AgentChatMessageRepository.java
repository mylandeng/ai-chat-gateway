package com.example.aichat.agent.repository;

import com.example.aichat.agent.model.AgentChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentChatMessageRepository extends JpaRepository<AgentChatMessage, Long> {

    List<AgentChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
