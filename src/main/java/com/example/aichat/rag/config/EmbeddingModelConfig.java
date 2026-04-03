package com.example.aichat.rag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingModelConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelConfig.class);

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${rag.embedding.api-key}") String apiKey,
            @Value("${rag.embedding.base-url}") String baseUrl,
            @Value("${rag.embedding.model-name}") String modelName,
            @Value("${rag.embedding.dimension}") int dimension) {

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .dimensions(dimension)
                .build();

        // 启动时校验实际返回的向量维度
        try {
            int actualDim = model.embed("维度校验").content().vector().length;
            log.info("[Embedding] 模型={}, 配置维度={}, 实际维度={}", modelName, dimension, actualDim);
            if (actualDim != dimension) {
                log.error("[Embedding] 维度不匹配！配置={} 实际={}，向量检索将失败！", dimension, actualDim);
            }
        } catch (Exception e) {
            log.warn("[Embedding] 启动校验失败（不影响运行）: {}", e.getMessage());
        }

        return model;
    }
}
