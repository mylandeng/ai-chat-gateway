package com.example.aichat.rag.service;

import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 简易 BM25 关键词检索（内存实现，适合中小规模）
 * 生产环境建议替换为 Elasticsearch
 */
@Service
public class Bm25RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(Bm25RetrievalService.class);

    // BM25 参数
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /**
     * 对给定语料库执行 BM25 检索
     *
     * @param query  查询文本
     * @param corpus 文本片段语料库
     * @param topK   返回 Top K 结果
     * @return 按 BM25 得分降序排列的文本片段
     */
    public List<TextSegment> search(String query, List<TextSegment> corpus, int topK) {
        if (corpus == null || corpus.isEmpty()) return List.of();

        String[] queryTerms = tokenize(query);
        if (queryTerms.length == 0) return List.of();

        // 计算平均文档长度
        double avgDocLen = corpus.stream()
                .mapToInt(seg -> tokenize(seg.text()).length)
                .average()
                .orElse(1.0);

        // 计算 IDF
        int totalDocs = corpus.size();
        Map<String, Double> idf = new HashMap<>();
        for (String term : queryTerms) {
            long docsContaining = corpus.stream()
                    .filter(seg -> seg.text().toLowerCase().contains(term))
                    .count();
            // IDF = log((N - n + 0.5) / (n + 0.5) + 1)
            idf.put(term, Math.log((totalDocs - docsContaining + 0.5) / (docsContaining + 0.5) + 1));
        }

        // 对每个文档计算 BM25 得分
        List<Map.Entry<TextSegment, Double>> scored = new ArrayList<>();
        for (TextSegment seg : corpus) {
            String[] docTerms = tokenize(seg.text());
            Map<String, Integer> termFreq = new HashMap<>();
            for (String t : docTerms) {
                termFreq.merge(t, 1, Integer::sum);
            }

            double score = 0;
            for (String term : queryTerms) {
                int tf = termFreq.getOrDefault(term, 0);
                if (tf == 0) continue;
                double idfVal = idf.getOrDefault(term, 0.0);
                // BM25 公式
                score += idfVal * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * docTerms.length / avgDocLen));
            }

            if (score > 0) {
                scored.add(Map.entry(seg, score));
            }
        }

        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<TextSegment> results = scored.stream()
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();

        log.debug("[BM25] query='{}', corpus={}, results={}", query, corpus.size(), results.size());
        return results;
    }

    /**
     * 获取 BM25 得分（用于混合检索的分数合并）
     */
    public Map<String, Double> searchWithScores(String query, List<TextSegment> corpus, int topK) {
        if (corpus == null || corpus.isEmpty()) return Map.of();

        String[] queryTerms = tokenize(query);
        if (queryTerms.length == 0) return Map.of();

        double avgDocLen = corpus.stream()
                .mapToInt(seg -> tokenize(seg.text()).length)
                .average()
                .orElse(1.0);

        int totalDocs = corpus.size();
        Map<String, Double> idf = new HashMap<>();
        for (String term : queryTerms) {
            long docsContaining = corpus.stream()
                    .filter(seg -> seg.text().toLowerCase().contains(term))
                    .count();
            idf.put(term, Math.log((totalDocs - docsContaining + 0.5) / (docsContaining + 0.5) + 1));
        }

        Map<String, Double> results = new LinkedHashMap<>();

        List<Map.Entry<TextSegment, Double>> scored = new ArrayList<>();
        for (TextSegment seg : corpus) {
            String[] docTerms = tokenize(seg.text());
            Map<String, Integer> termFreq = new HashMap<>();
            for (String t : docTerms) {
                termFreq.merge(t, 1, Integer::sum);
            }

            double score = 0;
            for (String term : queryTerms) {
                int tf = termFreq.getOrDefault(term, 0);
                if (tf == 0) continue;
                double idfVal = idf.getOrDefault(term, 0.0);
                score += idfVal * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * docTerms.length / avgDocLen));
            }

            if (score > 0) {
                scored.add(Map.entry(seg, score));
            }
        }

        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        scored.stream().limit(topK).forEach(e ->
                results.put(String.valueOf(e.getKey().text().hashCode()), e.getValue()));

        return results;
    }

    /**
     * 简易分词：按空格和中文标点切分，转小写
     */
    private String[] tokenize(String text) {
        return text.toLowerCase()
                .split("[\\s\\p{Punct}\\p{IsHan}&&[^\\p{IsHan}]]+|(?<=\\p{IsHan})|(?=\\p{IsHan})");
    }
}
