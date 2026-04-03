package com.example.aichat.config;

import com.example.aichat.model.enums.ModelProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Data
@ConfigurationProperties(prefix = "ai.models")
public class ModelProperties {

    private Map<String, ModelConfig> configs;

    @Data
    public static class ModelConfig {
        private ModelProvider provider;
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private Double temperature = 0.7;
        private Integer maxTokens = 2000;
    }
}
