package com.example.aichat.agent.repository;

import com.example.aichat.agent.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    List<Agent> findByTenantIdAndIsTemplateFalseOrderByCreatedAtDesc(Long tenantId);

    List<Agent> findByIsTemplateTrueOrderByCreatedAtAsc();

    Optional<Agent> findByIdAndTenantId(Long id, Long tenantId);

    long countByIsTemplateTrue();
}
