package com.example.aichat.agent.repository;

import com.example.aichat.agent.model.AgentWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentWorkflowRepository extends JpaRepository<AgentWorkflow, Long> {

    List<AgentWorkflow> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<AgentWorkflow> findByIdAndTenantId(Long id, Long tenantId);
}
