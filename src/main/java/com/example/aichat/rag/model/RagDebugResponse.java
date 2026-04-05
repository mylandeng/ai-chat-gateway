package com.example.aichat.rag.model;

import java.util.List;
import java.util.Map;

public record RagDebugResponse(
        String originalQuery,
        String rewrittenQuery,
        String retrievalMode,
        boolean rerankEnabled,
        List<DebugMatch> matches,
        String fullPrompt,
        TimingInfo timing,
        ConfigInfo config,
        int totalChunks
) {
    public record DebugMatch(
            int rank,
            String fileName,
            String text,
            double vectorScore,
            double bm25Score,
            double totalScore,
            double rerankScore,
            Map<String, String> metadata
    ) {}

    public record TimingInfo(
            long retrievalMs,
            long rewriteMs,
            long rerankMs,
            long totalMs
    ) {}

    public record ConfigInfo(
            int maxResults,
            double minScore,
            boolean hybridEnabled,
            double vectorWeight,
            double bm25Weight,
            boolean rerankEnabled,
            String rerankProvider,
            String rerankModel,
            boolean rewriteEnabled,
            int chunkSize,
            int chunkOverlap,
            String embeddingModel,
            int embeddingDimension
    ) {}
}
