package com.example.aichat.rag.controller;

import com.example.aichat.rag.model.KnowledgeBase;
import com.example.aichat.rag.model.RagResponse;
import com.example.aichat.rag.repository.KnowledgeBaseRepository;
import com.example.aichat.rag.service.RagChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 分享链接对话接口 — 无需 API Key 认证
 * 通过 shareToken 验证知识库是否公开
 */
@RestController
@RequestMapping("/api/rag/share")
public class SharedChatController {

    private final KnowledgeBaseRepository kbRepo;
    private final RagChatService ragChatService;

    public SharedChatController(KnowledgeBaseRepository kbRepo,
                                 RagChatService ragChatService) {
        this.kbRepo = kbRepo;
        this.ragChatService = ragChatService;
    }

    @PostMapping("/{token}/chat")
    public RagResponse chat(@PathVariable String token, @RequestBody Map<String, String> body) {
        KnowledgeBase kb = resolveSharedKb(token);
        String question = body.get("question");
        String model = body.getOrDefault("model", null);
        return ragChatService.chatByKb(question, model, kb.getId());
    }

    @GetMapping(value = "/{token}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String token,
                             @RequestParam String q,
                             @RequestParam(required = false) String model) {
        KnowledgeBase kb = resolveSharedKb(token);
        return ragChatService.streamChatByKb(q, model, kb.getId());
    }

    @GetMapping("/{token}/info")
    public Map<String, Object> info(@PathVariable String token) {
        KnowledgeBase kb = resolveSharedKb(token);
        return Map.of(
                "name", kb.getName(),
                "description", kb.getDescription() != null ? kb.getDescription() : "",
                "docCount", kb.getDocCount()
        );
    }

    private KnowledgeBase resolveSharedKb(String token) {
        return kbRepo.findByShareToken(token)
                .filter(kb -> "shared".equals(kb.getVisibility()))
                .orElseThrow(() -> new RuntimeException("分享链接无效或已关闭"));
    }
}
