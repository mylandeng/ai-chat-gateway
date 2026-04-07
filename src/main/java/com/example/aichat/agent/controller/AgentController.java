package com.example.aichat.agent.controller;

import com.example.aichat.agent.model.Agent;
import com.example.aichat.agent.service.AgentService;
import com.example.aichat.agent.service.ToolRegistry;
import com.example.aichat.context.RequestContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;
    private final ToolRegistry toolRegistry;

    public AgentController(AgentService agentService, ToolRegistry toolRegistry) {
        this.agentService = agentService;
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public List<Agent> list() {
        Long tenantId = RequestContext.get("tenantId");
        return agentService.listByTenant(tenantId);
    }

    @GetMapping("/templates")
    public List<Agent> listTemplates() {
        return agentService.listTemplates();
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> listTools() {
        return toolRegistry.listAvailableTools();
    }

    @GetMapping("/{id}")
    public Agent get(@PathVariable Long id) {
        Long tenantId = RequestContext.get("tenantId");
        return agentService.getByIdAndTenant(id, tenantId);
    }

    @PostMapping
    public Agent create(@RequestBody Agent agent) {
        Long tenantId = RequestContext.get("tenantId");
        return agentService.create(tenantId, agent);
    }

    @PutMapping("/{id}")
    public Agent update(@PathVariable Long id, @RequestBody Agent agent) {
        Long tenantId = RequestContext.get("tenantId");
        return agentService.update(id, tenantId, agent);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long tenantId = RequestContext.get("tenantId");
        agentService.delete(id, tenantId);
    }

    @PostMapping("/{templateId}/clone")
    public Agent cloneFromTemplate(@PathVariable Long templateId) {
        Long tenantId = RequestContext.get("tenantId");
        return agentService.cloneFromTemplate(templateId, tenantId);
    }
}
