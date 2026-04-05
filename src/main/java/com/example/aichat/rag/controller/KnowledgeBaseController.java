package com.example.aichat.rag.controller;

import com.example.aichat.context.RequestContext;
import com.example.aichat.rag.model.KnowledgeBase;
import com.example.aichat.rag.model.KnowledgeDocument;
import com.example.aichat.rag.model.RagDebugResponse;
import com.example.aichat.rag.repository.KnowledgeDocumentRepository;
import com.example.aichat.rag.service.IndexingPipeline;
import com.example.aichat.rag.service.KnowledgeBaseService;
import com.example.aichat.rag.service.RagChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag/kb")
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final KnowledgeBaseService kbService;
    private final KnowledgeDocumentRepository docRepo;
    private final IndexingPipeline indexingPipeline;
    private final RagChatService ragChatService;

    public KnowledgeBaseController(KnowledgeBaseService kbService,
                                    KnowledgeDocumentRepository docRepo,
                                    IndexingPipeline indexingPipeline,
                                    RagChatService ragChatService) {
        this.kbService = kbService;
        this.docRepo = docRepo;
        this.indexingPipeline = indexingPipeline;
        this.ragChatService = ragChatService;
    }

    // ========== 知识库 CRUD ==========

    @PostMapping
    public KnowledgeBase create(@RequestBody Map<String, String> body) {
        Long tenantId = RequestContext.get("tenantId");
        return kbService.create(tenantId, body.get("name"), body.get("description"));
    }

    @GetMapping
    public List<KnowledgeBase> list() {
        Long tenantId = RequestContext.get("tenantId");
        return kbService.listByTenant(tenantId);
    }

    @GetMapping("/{id}")
    public KnowledgeBase get(@PathVariable Long id) {
        Long tenantId = RequestContext.get("tenantId");
        return kbService.getByIdAndTenant(id, tenantId);
    }

    @PutMapping("/{id}")
    public KnowledgeBase update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long tenantId = RequestContext.get("tenantId");
        return kbService.update(id, tenantId, body.get("name"), body.get("description"));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.delete(id, tenantId);
        return Map.of("status", "deleted");
    }

    // ========== 知识库文档管理 ==========

    @PostMapping("/{kbId}/documents/upload")
    public KnowledgeDocument uploadDocument(@PathVariable Long kbId,
                                             @RequestParam("file") MultipartFile file) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.getByIdAndTenant(kbId, tenantId); // 验证归属

        KnowledgeDocument doc = indexingPipeline.saveFile(file, tenantId, kbId);
        indexingPipeline.processAsync(doc.getId(), doc.getFilePath());
        kbService.refreshDocCount(kbId);
        return doc;
    }

    @GetMapping("/{kbId}/documents")
    public List<KnowledgeDocument> listDocuments(@PathVariable Long kbId) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.getByIdAndTenant(kbId, tenantId);
        return docRepo.findByKbIdOrderByCreatedAtDesc(kbId);
    }

    @DeleteMapping("/{kbId}/documents/{docId}")
    public Map<String, String> deleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.getByIdAndTenant(kbId, tenantId);
        docRepo.deleteById(docId);
        kbService.refreshDocCount(kbId);
        return Map.of("status", "deleted");
    }

    // ========== 多轮 RAG 问答 ==========

    @PostMapping("/{kbId}/chat")
    public Map<String, Object> chat(@PathVariable Long kbId, @RequestBody Map<String, Object> body) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.getByIdAndTenant(kbId, tenantId);

        String question = (String) body.get("question");
        Long sessionId = body.get("sessionId") != null ?
                Long.valueOf(body.get("sessionId").toString()) : null;
        String model = (String) body.getOrDefault("model", null);

        return ragChatService.chat(kbId, sessionId, question, model, tenantId);
    }

    @GetMapping(value = "/{kbId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long kbId,
                             @RequestParam String q,
                             @RequestParam(required = false) Long sessionId,
                             @RequestParam(required = false) String model) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.getByIdAndTenant(kbId, tenantId);
        return ragChatService.streamChat(kbId, sessionId, q, model, tenantId);
    }

    // ========== 对话历史 ==========

    @GetMapping("/{kbId}/sessions")
    public List<?> listSessions(@PathVariable Long kbId) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.getByIdAndTenant(kbId, tenantId);
        return ragChatService.listSessions(kbId, tenantId);
    }

    @GetMapping("/{kbId}/sessions/{sessionId}/messages")
    public List<?> getMessages(@PathVariable Long kbId, @PathVariable Long sessionId) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.getByIdAndTenant(kbId, tenantId);
        return ragChatService.getMessages(sessionId);
    }

    @DeleteMapping("/{kbId}/sessions/{sessionId}")
    public Map<String, String> deleteSession(@PathVariable Long kbId, @PathVariable Long sessionId) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.getByIdAndTenant(kbId, tenantId);
        ragChatService.deleteSession(sessionId);
        return Map.of("status", "deleted");
    }

    // ========== 分享设置 ==========

    @PostMapping("/{id}/share")
    public Map<String, Object> toggleShare(@PathVariable Long id, @RequestParam boolean enabled) {
        Long tenantId = RequestContext.get("tenantId");
        KnowledgeBase kb = kbService.toggleShare(id, tenantId, enabled);
        return Map.of(
                "visibility", kb.getVisibility(),
                "shareToken", kb.getShareToken() != null ? kb.getShareToken() : "",
                "shareUrl", kb.getShareToken() != null ? "/share/" + kb.getShareToken() : ""
        );
    }

    // ========== RAG Debug ==========

    @GetMapping("/{kbId}/debug")
    public RagDebugResponse debug(@PathVariable Long kbId,
                                  @RequestParam String q,
                                  @RequestParam(required = false) String model) {
        Long tenantId = RequestContext.get("tenantId");
        kbService.getByIdAndTenant(kbId, tenantId);
        return ragChatService.debug(kbId, q, model);
    }
}
