package com.example.aichat.rag.model;

import dev.langchain4j.data.document.Metadata;

/**
 * 混合检索结果（向量 + BM25 加权合并）
 */
public class HybridMatch {

    private final String text;
    private final Metadata metadata;
    private double vectorScore = 0;
    private double bm25Score = 0;

    public HybridMatch(String text, Metadata metadata) {
        this.text = text;
        this.metadata = metadata;
    }

    public void addVectorScore(double score) {
        this.vectorScore += score;
    }

    public void addBm25Score(double score) {
        this.bm25Score += score;
    }

    public double totalScore() {
        return vectorScore + bm25Score;
    }

    public String getText() {
        return text;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public double getVectorScore() {
        return vectorScore;
    }

    public double getBm25Score() {
        return bm25Score;
    }
}
