package com.example.aichat.rag.model;

/**
 * RAG 来源引用
 */
public record RagSource(String fileName, double score, String preview) {
}
