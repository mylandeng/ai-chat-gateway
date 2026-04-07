package com.example.aichat.agent.controller;

import com.example.aichat.agent.model.AgentChatMessage;
import com.example.aichat.agent.model.AgentChatSession;
import com.example.aichat.agent.service.AgentChatService;
import com.example.aichat.context.RequestContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/agents/{agentId}")
public class AgentChatController {

    private final AgentChatService chatService;

    public AgentChatController(AgentChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable Long agentId,
                                  @RequestParam String q,
                                  @RequestParam(required = false) Long sessionId,
                                  @RequestParam(required = false) String model) {
        Long tenantId = RequestContext.get("tenantId");
        return chatService.streamChat(agentId, sessionId, q, model, tenantId);
    }

    @GetMapping("/sessions")
    public List<AgentChatSession> listSessions(@PathVariable Long agentId) {
        Long tenantId = RequestContext.get("tenantId");
        return chatService.listSessions(agentId, tenantId);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<AgentChatMessage> getMessages(@PathVariable Long agentId,
                                               @PathVariable Long sessionId) {
        return chatService.getMessages(sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(@PathVariable Long agentId,
                               @PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
    }
}
