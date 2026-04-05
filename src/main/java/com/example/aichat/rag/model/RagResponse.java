package com.example.aichat.rag.model;

import java.util.List;

/**
 * RAG 问答响应
 */
public record RagResponse(String answer, List<RagSource> sources, String question) {
}
