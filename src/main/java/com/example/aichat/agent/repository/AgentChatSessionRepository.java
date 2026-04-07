package com.example.aichat.agent.repository;

import com.example.aichat.agent.model.AgentChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentChatSessionRepository extends JpaRepository<AgentChatSession, Long> {

    List<AgentChatSession> findByAgentIdAndTenantIdOrderByUpdatedAtDesc(Long agentId, Long tenantId);

    void deleteByAgentId(Long agentId);
}
