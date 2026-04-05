package com.example.aichat.rag.service;

import com.example.aichat.rag.model.*;
import com.example.aichat.rag.repository.KnowledgeDocumentRepository;
import com.example.aichat.rag.repository.RagChatMessageRepository;
import com.example.aichat.rag.repository.RagChatSessionRepository;
import com.example.aichat.service.ChatModelFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

    private final VectorStoreService vectorStoreService;
    private final HybridRetrievalService hybridRetrievalService;
    private final RerankService rerankService;
    private final ChatModelFactory modelFactory;
    private final RagChatSessionRepository sessionRepo;
    private final RagChatMessageRepository messageRepo;
    private final KnowledgeDocumentRepository documentRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${rag.retrieval.max-results:5}")
    private int maxResults;

    @Value("${rag.retrieval.min-score:0.0}")
    private double minScore;

    @Value("${rag.chat.history-rounds:5}")
    private int historyRounds;

    @Value("${rag.chat.rewrite-enabled:true}")
    private boolean rewriteEnabled;

    @Value("${rag.hybrid.enabled:false}")
    private boolean hybridEnabled;

    @Value("${rag.rerank.enabled:false}")
    private boolean rerankEnabled;

    @Value("${rag.hybrid.vector-weight:0.7}")
    private double vectorWeight;

    @Value("${rag.hybrid.bm25-weight:0.3}")
    private double bm25Weight;

    @Value("${rag.rerank.provider:cohere}")
    private String rerankProvider;

    @Value("${rag.rerank.model:rerank-v3.5}")
    private String rerankModel;

    @Value("${rag.chunk.size:500}")
    private int chunkSize;

    @Value("${rag.chunk.overlap:50}")
    private int chunkOverlap;

    @Value("${rag.embedding.model-name:text-embedding-v3}")
    private String embeddingModel;

    @Value("${rag.embedding.dimension:1024}")
    private int embeddingDimension;

    private static final String DEFAULT_MODEL = "deepseek-chat";

    private static final String REWRITE_PROMPT = """
            你是一个查询改写助手。根据对话历史，将用户的最新问题改写为一个独立的、完整的检索查询。
            规则：补全代词和省略的上下文；保持原意；如果问题已经完整，直接返回原问题；只返回改写后的查询。

            对话历史：
            %s

            用户最新问题：%s

            改写后的查询：""";

    private static final String RAG_SYSTEM_PROMPT = """
            你是一个企业知识库助手。请根据以下参考资料和对话历史回答用户的问题。

            规则：
            - 只基于参考资料回答，不要编造信息
            - 如果参考资料不足以回答，请如实说明"根据现有资料无法回答该问题"
            - 回答时尽量引用原文中的关键信息
            - 注意上下文语境，理解用户追问的意图
            - 使用简洁清晰的语言

            参考资料：
            %s

            对话历史：
            %s
            """;

    public RagChatService(VectorStoreService vectorStoreService,
                          HybridRetrievalService hybridRetrievalService,
                          RerankService rerankService,
                          ChatModelFactory modelFactory,
                          RagChatSessionRepository sessionRepo,
                          RagChatMessageRepository messageRepo,
                          KnowledgeDocumentRepository documentRepo) {
        this.vectorStoreService = vectorStoreService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.rerankService = rerankService;
        this.modelFactory = modelFactory;
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.documentRepo = documentRepo;
    }

    /**
     * 多轮 RAG 问答
     */
    public Map<String, Object> chat(Long kbId, Long sessionId, String question,
                                     String modelId, Long tenantId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        log.info("[RAG Chat] kbId={}, sessionId={}, question='{}'", kbId, sessionId, question);

        // 1. 获取或创建 session
        RagChatSession session = getOrCreateSession(kbId, sessionId, tenantId);

        // 2. 加载历史
        List<RagChatMessage> history = loadHistory(session.getId());

        // 3. Query 改写
        String rewrittenQuery = rewriteQuery(question, history, modelId);

        // 4. 检索
        List<String> contextTexts = new ArrayList<>();
        List<RagSource> sources = new ArrayList<>();
        retrieveByKb(rewrittenQuery, kbId, contextTexts, sources);

        if (contextTexts.isEmpty()) {
            saveMessage(session.getId(), "user", question, rewrittenQuery, null);
            String noResultAnswer = "知识库中没有找到与您问题相关的内容，请先上传相关文档。";
            saveMessage(session.getId(), "assistant", noResultAnswer, null, null);
            return Map.of("answer", noResultAnswer, "sources", List.of(),
                    "sessionId", session.getId(), "rewrittenQuery", rewrittenQuery);
        }

        // 5. 构建 Prompt
        StringBuilder contextStr = new StringBuilder();
        for (int i = 0; i < contextTexts.size(); i++) {
            contextStr.append(String.format("[%d] %s\n\n", i + 1, contextTexts.get(i)));
        }
        String historyStr = buildHistoryString(history);
        String systemPrompt = String.format(RAG_SYSTEM_PROMPT, contextStr, historyStr);

        // 6. LLM 生成
        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                dev.langchain4j.data.message.UserMessage.from(question)
        );
        String answer = modelFactory.getModel(modelId).generate(messages).content().text();

        // 7. 保存对话
        saveMessage(session.getId(), "user", question, rewrittenQuery, null);
        saveMessage(session.getId(), "assistant", answer, null, sourcesToJson(sources));

        // 8. 更新 title
        if (session.getTitle() == null) {
            session.setTitle(question.length() > 50 ? question.substring(0, 50) + "..." : question);
            sessionRepo.save(session);
        }

        return Map.of("answer", answer, "sources", sources,
                "sessionId", session.getId(), "rewrittenQuery", rewrittenQuery);
    }

    /**
     * 流式多轮 RAG
     */
    public SseEmitter streamChat(Long kbId, Long sessionId, String question,
                                  String modelId, Long tenantId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        SseEmitter emitter = new SseEmitter(120_000L);
        final String finalModelId = modelId;

        executor.submit(() -> {
            try {
                RagChatSession session = getOrCreateSession(kbId, sessionId, tenantId);
                List<RagChatMessage> history = loadHistory(session.getId());
                String rewrittenQuery = rewriteQuery(question, history, finalModelId);

                List<String> contextTexts = new ArrayList<>();
                List<RagSource> sources = new ArrayList<>();
                retrieveByKb(rewrittenQuery, kbId, contextTexts, sources);

                if (contextTexts.isEmpty()) {
                    emitter.send(SseEmitter.event().data(
                            Map.of("content", "知识库中没有找到相关内容。")));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                    return;
                }

                // 发送来源
                emitter.send(SseEmitter.event().name("sources").data(sources));
                // 发送 sessionId
                emitter.send(SseEmitter.event().name("session").data(
                        Map.of("sessionId", session.getId())));

                StringBuilder contextStr = new StringBuilder();
                for (int i = 0; i < contextTexts.size(); i++) {
                    contextStr.append(String.format("[%d] %s\n\n", i + 1, contextTexts.get(i)));
                }
                String historyStr = buildHistoryString(history);
                String systemPrompt = String.format(RAG_SYSTEM_PROMPT, contextStr, historyStr);

                StreamingChatLanguageModel streamModel = modelFactory.getStreamingModel(finalModelId);
                List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                        dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                        dev.langchain4j.data.message.UserMessage.from(question)
                );

                StringBuilder fullAnswer = new StringBuilder();
                streamModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        fullAnswer.append(token);
                        try {
                            emitter.send(SseEmitter.event().data(Map.of("content", token)));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            // 保存对话
                            saveMessage(session.getId(), "user", question, rewrittenQuery, null);
                            saveMessage(session.getId(), "assistant", fullAnswer.toString(),
                                    null, sourcesToJson(sources));
                            if (session.getTitle() == null) {
                                session.setTitle(question.length() > 50 ?
                                        question.substring(0, 50) + "..." : question);
                                sessionRepo.save(session);
                            }

                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("[RAG Chat] 流式生成失败", error);
                        emitter.completeWithError(error);
                    }
                });
            } catch (Exception e) {
                log.error("[RAG Chat] 流式问答失败", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * RAG Debug
     */
    public RagDebugResponse debug(Long kbId, String query, String modelId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        long start = System.currentTimeMillis();

        // 1. Query 改写
        long rewriteStart = System.currentTimeMillis();
        String rewritten = rewriteQuery(query, List.of(), modelId);
        long rewriteMs = System.currentTimeMillis() - rewriteStart;

        // 2. 检索
        long retrievalStart = System.currentTimeMillis();
        int debugTopK = maxResults * 4;
        List<RagDebugResponse.DebugMatch> debugMatches = new ArrayList<>();

        if (hybridEnabled) {
            List<HybridMatch> matches = hybridRetrievalService.hybridSearch(rewritten, debugTopK, kbId);
            for (int i = 0; i < matches.size(); i++) {
                HybridMatch m = matches.get(i);
                debugMatches.add(new RagDebugResponse.DebugMatch(
                        i + 1, getFileName(m.getMetadata()), m.getText(),
                        m.getVectorScore(), m.getBm25Score(), m.totalScore(),
                        -1, metadataToMap(m.getMetadata())
                ));
            }
        } else {
            List<EmbeddingMatch<TextSegment>> matches =
                    vectorStoreService.searchByKb(rewritten, debugTopK, 0.0, kbId);
            for (int i = 0; i < matches.size(); i++) {
                var m = matches.get(i);
                debugMatches.add(new RagDebugResponse.DebugMatch(
                        i + 1, getFileName(m.embedded().metadata()), m.embedded().text(),
                        m.score(), 0, m.score(),
                        -1, metadataToMap(m.embedded().metadata())
                ));
            }
        }
        long retrievalMs = System.currentTimeMillis() - retrievalStart;

        // 3. Rerank
        long rerankMs = 0;
        if (rerankEnabled && !debugMatches.isEmpty()) {
            long rerankStart = System.currentTimeMillis();
            List<String> texts = debugMatches.stream()
                    .map(RagDebugResponse.DebugMatch::text).toList();
            List<RerankResult> reranked = rerankService.rerank(rewritten, texts, debugTopK);

            List<RagDebugResponse.DebugMatch> rerankedMatches = new ArrayList<>();
            for (int i = 0; i < reranked.size(); i++) {
                RerankResult r = reranked.get(i);
                RagDebugResponse.DebugMatch orig = debugMatches.get(r.originalIndex());
                rerankedMatches.add(new RagDebugResponse.DebugMatch(
                        i + 1, orig.fileName(), orig.text(),
                        orig.vectorScore(), orig.bm25Score(), orig.totalScore(),
                        r.score(), orig.metadata()
                ));
            }
            debugMatches = rerankedMatches;
            rerankMs = System.currentTimeMillis() - rerankStart;
        }

        long totalMs = System.currentTimeMillis() - start;

        // 4. 构建示例 Prompt
        StringBuilder contextStr = new StringBuilder();
        List<RagDebugResponse.DebugMatch> topMatches = debugMatches.stream()
                .limit(maxResults).toList();
        for (int i = 0; i < topMatches.size(); i++) {
            contextStr.append(String.format("[%d] %s\n\n", i + 1, topMatches.get(i).text()));
        }
        String fullPrompt = String.format(RAG_SYSTEM_PROMPT, contextStr, "(无历史)");

        String mode = hybridEnabled ? (rerankEnabled ? "hybrid_rerank" : "hybrid") : "vector";

        // 构建配置信息
        RagDebugResponse.ConfigInfo configInfo = new RagDebugResponse.ConfigInfo(
                maxResults, minScore, hybridEnabled, vectorWeight, bm25Weight,
                rerankEnabled, rerankProvider, rerankModel, rewriteEnabled,
                chunkSize, chunkOverlap, embeddingModel, embeddingDimension
        );

        // 查询知识库总片段数
        int totalChunks = documentRepo.findByKbIdOrderByCreatedAtDesc(kbId).stream()
                .mapToInt(d -> d.getChunkCount() != null ? d.getChunkCount() : 0).sum();

        return new RagDebugResponse(query, rewritten, mode, rerankEnabled,
                debugMatches, fullPrompt,
                new RagDebugResponse.TimingInfo(retrievalMs, rewriteMs, rerankMs, totalMs),
                configInfo, totalChunks);
    }

    // ========== Session / Message 管理 ==========

    public List<RagChatSession> listSessions(Long kbId, Long tenantId) {
        return sessionRepo.findByKbIdAndTenantIdOrderByUpdatedAtDesc(kbId, tenantId);
    }

    public List<RagChatMessage> getMessages(Long sessionId) {
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        messageRepo.deleteBySessionId(sessionId);
        sessionRepo.deleteById(sessionId);
    }

    // ========== 分享问答（无历史） ==========

    public RagResponse chatByKb(String question, String modelId, Long kbId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        List<String> contextTexts = new ArrayList<>();
        List<RagSource> sources = new ArrayList<>();
        retrieveByKb(question, kbId, contextTexts, sources);

        if (contextTexts.isEmpty()) {
            return new RagResponse("知识库中没有找到相关内容。", List.of(), question);
        }

        StringBuilder contextStr = new StringBuilder();
        for (int i = 0; i < contextTexts.size(); i++) {
            contextStr.append(String.format("[%d] %s\n\n", i + 1, contextTexts.get(i)));
        }
        String systemPrompt = String.format(RAG_SYSTEM_PROMPT, contextStr, "(无历史)");

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                dev.langchain4j.data.message.UserMessage.from(question)
        );
        String answer = modelFactory.getModel(modelId).generate(messages).content().text();
        return new RagResponse(answer, sources, question);
    }

    public SseEmitter streamChatByKb(String question, String modelId, Long kbId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        SseEmitter emitter = new SseEmitter(120_000L);
        final String fModelId = modelId;

        executor.submit(() -> {
            try {
                List<String> contextTexts = new ArrayList<>();
                List<RagSource> sources = new ArrayList<>();
                retrieveByKb(question, kbId, contextTexts, sources);

                if (contextTexts.isEmpty()) {
                    emitter.send(SseEmitter.event().data(Map.of("content", "知识库中没有找到相关内容。")));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                    return;
                }

                emitter.send(SseEmitter.event().name("sources").data(sources));

                StringBuilder contextStr = new StringBuilder();
                for (int i = 0; i < contextTexts.size(); i++) {
                    contextStr.append(String.format("[%d] %s\n\n", i + 1, contextTexts.get(i)));
                }
                String systemPrompt = String.format(RAG_SYSTEM_PROMPT, contextStr, "(无历史)");

                StreamingChatLanguageModel streamModel = modelFactory.getStreamingModel(fModelId);
                List<dev.langchain4j.data.message.ChatMessage> msgs = List.of(
                        dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                        dev.langchain4j.data.message.UserMessage.from(question)
                );

                streamModel.generate(msgs, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        try { emitter.send(SseEmitter.event().data(Map.of("content", token))); }
                        catch (IOException e) { emitter.completeWithError(e); }
                    }
                    @Override
                    public void onComplete(Response<AiMessage> r) {
                        try { emitter.send(SseEmitter.event().data("[DONE]")); emitter.complete(); }
                        catch (IOException e) { emitter.completeWithError(e); }
                    }
                    @Override
                    public void onError(Throwable error) { emitter.completeWithError(error); }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ========== 内部方法 ==========

    String rewriteQuery(String question, List<RagChatMessage> history, String modelId) {
        if (!rewriteEnabled || history.isEmpty()) return question;

        String historyStr = buildHistoryString(history);
        String prompt = String.format(REWRITE_PROMPT, historyStr, question);

        try {
            String rewritten = modelFactory.getModel(modelId).generate(prompt).trim();
            log.debug("[Query改写] '{}' → '{}'", question, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("[Query改写] 失败，使用原始 query: {}", e.getMessage());
            return question;
        }
    }

    private RagChatSession getOrCreateSession(Long kbId, Long sessionId, Long tenantId) {
        if (sessionId != null) {
            return sessionRepo.findById(sessionId).orElseGet(() -> createSession(kbId, tenantId));
        }
        return createSession(kbId, tenantId);
    }

    private RagChatSession createSession(Long kbId, Long tenantId) {
        RagChatSession session = new RagChatSession();
        session.setKbId(kbId);
        session.setTenantId(tenantId);
        return sessionRepo.save(session);
    }

    private List<RagChatMessage> loadHistory(Long sessionId) {
        List<RagChatMessage> all = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int maxMessages = historyRounds * 2;
        if (all.size() <= maxMessages) return all;
        return all.subList(all.size() - maxMessages, all.size());
    }

    private void retrieveByKb(String query, Long kbId,
                               List<String> contextTexts, List<RagSource> sources) {
        List<EmbeddingMatch<TextSegment>> matches =
                vectorStoreService.searchByKb(query, maxResults, minScore, kbId);

        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment seg = match.embedded();
            contextTexts.add(seg.text());
            sources.add(new RagSource(
                    getFileName(seg.metadata()),
                    Math.round(match.score() * 100) / 100.0,
                    seg.text().substring(0, Math.min(100, seg.text().length())) + "..."
            ));
        }
    }

    private void saveMessage(Long sessionId, String role, String content,
                             String rewrittenQuery, String sourcesJson) {
        RagChatMessage msg = new RagChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setRewrittenQuery(rewrittenQuery);
        msg.setSources(sourcesJson);
        messageRepo.save(msg);
    }

    private String buildHistoryString(List<RagChatMessage> history) {
        if (history.isEmpty()) return "(无历史)";
        StringBuilder sb = new StringBuilder();
        for (RagChatMessage msg : history) {
            sb.append(msg.getRole().equals("user") ? "用户: " : "助手: ");
            String content = msg.getContent();
            if (content.length() > 200) content = content.substring(0, 200) + "...";
            sb.append(content).append("\n");
        }
        return sb.toString();
    }

    private String sourcesToJson(List<RagSource> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String getFileName(dev.langchain4j.data.document.Metadata metadata) {
        String name = metadata.getString("file_name");
        return name != null ? name : "未知";
    }

    private Map<String, String> metadataToMap(dev.langchain4j.data.document.Metadata metadata) {
        Map<String, String> map = new HashMap<>();
        if (metadata.getString("file_name") != null) map.put("file_name", metadata.getString("file_name"));
        if (metadata.getString("doc_id") != null) map.put("doc_id", metadata.getString("doc_id"));
        if (metadata.getString("kb_id") != null) map.put("kb_id", metadata.getString("kb_id"));
        return map;
    }
}
