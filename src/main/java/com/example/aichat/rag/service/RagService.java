package com.example.aichat.rag.service;

import com.example.aichat.context.RequestContext;
import com.example.aichat.rag.model.HybridMatch;
import com.example.aichat.rag.model.RagResponse;
import com.example.aichat.rag.model.RagSource;
import com.example.aichat.rag.model.RerankResult;
import com.example.aichat.service.ChatModelFactory;
import com.example.aichat.service.LongTermMemoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStoreService vectorStoreService;
    private final ChatModelFactory modelFactory;
    private final HybridRetrievalService hybridRetrievalService;
    private final RerankService rerankService;
    private final LongTermMemoryService memoryService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${rag.retrieval.max-results:5}")
    private int maxResults;

    @Value("${rag.retrieval.min-score:0.6}")
    private double minScore;

    @Value("${rag.hybrid.enabled:false}")
    private boolean hybridEnabled;

    @Value("${rag.rerank.enabled:false}")
    private boolean rerankEnabled;

    private static final String DEFAULT_MODEL = "deepseek-chat";

    private static final String RAG_SYSTEM_PROMPT = """
            你是一个企业知识库助手。请根据以下参考资料回答用户的问题。

            规则：
            - 只基于参考资料回答，不要编造信息
            - 如果参考资料不足以回答，请如实说明"根据现有资料无法回答该问题"
            - 回答时尽量引用原文中的关键信息
            - 使用简洁清晰的语言

            长期记忆：
            %s

            参考资料：
            %s
            """;

    public RagService(VectorStoreService vectorStoreService,
                      ChatModelFactory modelFactory,
                      HybridRetrievalService hybridRetrievalService,
                      RerankService rerankService,
                      LongTermMemoryService memoryService) {
        this.vectorStoreService = vectorStoreService;
        this.modelFactory = modelFactory;
        this.hybridRetrievalService = hybridRetrievalService;
        this.rerankService = rerankService;
        this.memoryService = memoryService;
    }

    /**
     * RAG 问答（非流式）
     */
    public RagResponse chat(String question, String modelId, Long tenantId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        log.info("[RAG] 问答: question='{}', model={}, tenantId={}", question, modelId, tenantId);
        String userId = memoryService.resolveUserId(tenantId, RequestContext.get("keyId"));
        String memoryPrompt = memoryOrFallback(memoryService.buildMemoryPrompt(userId, question));

        // 1. 检索（向量 or 混合）
        RetrievalContext ctx = retrieve(question, tenantId);
        if (ctx.contextTexts.isEmpty()) {
            log.info("[RAG] 未找到相关文档");
            return new RagResponse("知识库中没有找到与您问题相关的内容，请先上传相关文档。",
                    List.of(), question);
        }

        // 2. 构建上下文
        StringBuilder contextStr = new StringBuilder();
        for (int i = 0; i < ctx.contextTexts.size(); i++) {
            contextStr.append(String.format("[%d] %s\n\n", i + 1, ctx.contextTexts.get(i)));
        }

        // 3. 调用 LLM
        String systemPrompt = String.format(RAG_SYSTEM_PROMPT, memoryPrompt, contextStr);
        ChatLanguageModel model = modelFactory.getModel(modelId);

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                dev.langchain4j.data.message.UserMessage.from(question)
        );

        String answer = model.generate(messages).content().text();
        memoryService.addConversation(userId, question, answer);
        log.info("[RAG] 回答生成完成, 来源数={}", ctx.sources.size());

        return new RagResponse(answer, ctx.sources, question);
    }

    /**
     * RAG 问答（流式 SSE）
     * 注意：tenantId 显式传入，不依赖 ThreadLocal（因为在线程池执行）
     */
    public SseEmitter streamChat(String question, String modelId, Long tenantId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        SseEmitter emitter = new SseEmitter(120_000L);

        final String finalModelId = modelId;
        final String keyId = RequestContext.get("keyId");

        executor.submit(() -> {
            try {
                String userId = memoryService.resolveUserId(tenantId, keyId);
                String memoryPrompt = memoryOrFallback(memoryService.buildMemoryPrompt(userId, question));

                // 1. 检索（向量 or 混合）
                RetrievalContext ctx = retrieve(question, tenantId);

                if (ctx.contextTexts.isEmpty()) {
                    emitter.send(SseEmitter.event().data(
                            Map.of("content", "知识库中没有找到与您问题相关的内容，请先上传相关文档。")));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                    return;
                }

                // 发送来源信息
                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data(ctx.sources));

                // 2. 构建上下文
                StringBuilder contextStr = new StringBuilder();
                for (int i = 0; i < ctx.contextTexts.size(); i++) {
                    contextStr.append(String.format("[%d] %s\n\n", i + 1, ctx.contextTexts.get(i)));
                }

                // 3. 流式调用 LLM
                String systemPrompt = String.format(RAG_SYSTEM_PROMPT, memoryPrompt, contextStr);
                StreamingChatLanguageModel streamModel = modelFactory.getStreamingModel(finalModelId);

                List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                        dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                        dev.langchain4j.data.message.UserMessage.from(question)
                );

                streamModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                    private final StringBuilder fullAnswer = new StringBuilder();

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
                            memoryService.addConversation(userId, question, fullAnswer.toString());
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("[RAG] 流式生成失败", error);
                        emitter.completeWithError(error);
                    }
                });

            } catch (Exception e) {
                log.error("[RAG] 流式问答失败", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ========== 内部检索逻辑 ==========

    /**
     * 统一检索入口：支持纯向量 / 混合检索 / 混合+Rerank
     */
    private RetrievalContext retrieve(String question, Long tenantId) {
        List<String> contextTexts = new ArrayList<>();
        List<RagSource> sources = new ArrayList<>();

        if (hybridEnabled) {
            // 混合检索模式
            int retrieveCount = rerankEnabled ? maxResults * 4 : maxResults;
            List<HybridMatch> matches = hybridRetrievalService.hybridSearch(
                    question, retrieveCount, tenantId);

            if (matches.isEmpty()) return new RetrievalContext(List.of(), List.of());

            if (rerankEnabled) {
                // Rerank 重排序
                List<String> candidateTexts = matches.stream()
                        .map(HybridMatch::getText).toList();
                List<RerankResult> reranked = rerankService.rerank(
                        question, candidateTexts, maxResults);
                for (RerankResult r : reranked) {
                    contextTexts.add(r.text());
                    // Rerank 后从原始 matches 中提取 metadata
                    HybridMatch original = matches.get(r.originalIndex());
                    sources.add(new RagSource(
                            original.getMetadata().getString("file_name") != null ?
                                    original.getMetadata().getString("file_name") : "未知",
                            r.score(),
                            r.text().substring(0, Math.min(100, r.text().length())) + "..."
                    ));
                }
            } else {
                // 仅混合检索，取 Top N
                for (HybridMatch m : matches.stream().limit(maxResults).toList()) {
                    contextTexts.add(m.getText());
                    sources.add(new RagSource(
                            m.getMetadata().getString("file_name") != null ?
                                    m.getMetadata().getString("file_name") : "未知",
                            Math.round(m.totalScore() * 100) / 100.0,
                            m.getText().substring(0, Math.min(100, m.getText().length())) + "..."
                    ));
                }
            }
        } else {
            // 纯向量检索模式（默认）
            List<EmbeddingMatch<TextSegment>> matches =
                    vectorStoreService.search(question, maxResults, minScore, tenantId);

            for (EmbeddingMatch<TextSegment> match : matches) {
                TextSegment segment = match.embedded();
                contextTexts.add(segment.text());
                sources.add(new RagSource(
                        segment.metadata().getString("file_name") != null ?
                                segment.metadata().getString("file_name") : "未知",
                        Math.round(match.score() * 100) / 100.0,
                        segment.text().substring(0, Math.min(100, segment.text().length())) + "..."
                ));
            }
        }

        return new RetrievalContext(contextTexts, sources);
    }

    private String memoryOrFallback(String memoryPrompt) {
        return memoryPrompt == null || memoryPrompt.isBlank() ? "(无相关记忆)" : memoryPrompt;
    }

    /**
     * 检索结果上下文（内部使用）
     */
    private record RetrievalContext(List<String> contextTexts, List<RagSource> sources) {}
}
