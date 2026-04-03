package com.example.aichat.rag.service;

import com.example.aichat.service.ChatModelFactory;
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
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${rag.retrieval.max-results:5}")
    private int maxResults;

    @Value("${rag.retrieval.min-score:0.6}")
    private double minScore;

    private static final String DEFAULT_MODEL = "deepseek-chat";

    private static final String RAG_SYSTEM_PROMPT = """
            你是一个企业知识库助手。请根据以下参考资料回答用户的问题。

            规则：
            - 只基于参考资料回答，不要编造信息
            - 如果参考资料不足以回答，请如实说明"根据现有资料无法回答该问题"
            - 回答时尽量引用原文中的关键信息
            - 使用简洁清晰的语言

            参考资料：
            %s
            """;

    public RagService(VectorStoreService vectorStoreService, ChatModelFactory modelFactory) {
        this.vectorStoreService = vectorStoreService;
        this.modelFactory = modelFactory;
    }

    /**
     * RAG 问答（非流式）
     */
    public Map<String, Object> chat(String question, String modelId, Long tenantId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        log.info("[RAG] 问答: question='{}', model={}, tenantId={}", question, modelId, tenantId);

        // 1. 向量检索（带租户隔离）
        List<EmbeddingMatch<TextSegment>> matches =
                vectorStoreService.search(question, maxResults, minScore, tenantId);

        if (matches.isEmpty()) {
            log.info("[RAG] 未找到相关文档");
            return Map.of(
                    "answer", "知识库中没有找到与您问题相关的内容，请先上传相关文档。",
                    "sources", List.of(),
                    "question", question
            );
        }

        // 2. 构建上下文
        StringBuilder context = new StringBuilder();
        List<Map<String, Object>> sources = new ArrayList<>();

        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            TextSegment segment = match.embedded();
            context.append(String.format("[%d] %s\n\n", i + 1, segment.text()));

            sources.add(Map.of(
                    "fileName", segment.metadata().getString("file_name") != null ?
                            segment.metadata().getString("file_name") : "未知",
                    "score", Math.round(match.score() * 100) / 100.0,
                    "preview", segment.text().substring(0,
                            Math.min(100, segment.text().length())) + "..."
            ));
        }

        // 3. 调用 LLM
        String systemPrompt = String.format(RAG_SYSTEM_PROMPT, context);
        ChatLanguageModel model = modelFactory.getModel(modelId);

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                dev.langchain4j.data.message.UserMessage.from(question)
        );

        String answer = model.generate(messages).content().text();
        log.info("[RAG] 回答生成完成, 来源数={}", sources.size());

        return Map.of(
                "answer", answer,
                "sources", sources,
                "question", question
        );
    }

    /**
     * RAG 问答（流式 SSE）
     * 注意：tenantId 显式传入，不依赖 ThreadLocal（因为在线程池执行）
     */
    public SseEmitter streamChat(String question, String modelId, Long tenantId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        SseEmitter emitter = new SseEmitter(120_000L);

        final String finalModelId = modelId;

        // 用线程池执行，显式传入 tenantId 避免 ThreadLocal 丢失
        executor.submit(() -> {
            try {
                // 1. 向量检索（带租户隔离）
                List<EmbeddingMatch<TextSegment>> matches =
                        vectorStoreService.search(question, maxResults, minScore, tenantId);

                // 先发送来源信息
                List<Map<String, Object>> sources = new ArrayList<>();
                StringBuilder context = new StringBuilder();

                if (matches.isEmpty()) {
                    emitter.send(SseEmitter.event().data(
                            Map.of("content", "知识库中没有找到与您问题相关的内容，请先上传相关文档。")));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                    return;
                }

                for (int i = 0; i < matches.size(); i++) {
                    EmbeddingMatch<TextSegment> match = matches.get(i);
                    TextSegment segment = match.embedded();
                    context.append(String.format("[%d] %s\n\n", i + 1, segment.text()));
                    sources.add(Map.of(
                            "fileName", segment.metadata().getString("file_name") != null ?
                                    segment.metadata().getString("file_name") : "未知",
                            "score", Math.round(match.score() * 100) / 100.0
                    ));
                }

                // 发送来源信息
                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data(sources));

                // 2. 流式调用 LLM
                String systemPrompt = String.format(RAG_SYSTEM_PROMPT, context);
                StreamingChatLanguageModel streamModel = modelFactory.getStreamingModel(finalModelId);

                List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                        dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                        dev.langchain4j.data.message.UserMessage.from(question)
                );

                streamModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        try {
                            emitter.send(SseEmitter.event().data(Map.of("content", token)));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
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
}
