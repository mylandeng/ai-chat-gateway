package com.example.aichat.rag.model;

/**
 * Rerank 重排序结果
 */
public record RerankResult(int originalIndex, double score, String text) {
}
