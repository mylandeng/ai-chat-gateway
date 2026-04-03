package com.example.aichat.model.enums;

/**
 * 模型提供者枚举
 * OPENAI 同时覆盖所有兼容 OpenAI 格式的模型（DeepSeek、智谱GLM、Moonshot等）
 */
public enum ModelProvider {
    OPENAI,      // OpenAI + 所有兼容 OpenAI 格式的模型
    DASHSCOPE,   // 通义千问原生 SDK
    CLAUDE       // Anthropic Claude
}
