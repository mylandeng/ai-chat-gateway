package com.example.aichat.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);

    private final DocumentSplitter splitter;

    public ChunkService(
            @Value("${rag.chunk.size:500}") int chunkSize,
            @Value("${rag.chunk.overlap:50}") int overlap) {
        this.splitter = DocumentSplitters.recursive(chunkSize, overlap);
        log.info("[切片服务] 初始化: chunkSize={}, overlap={}", chunkSize, overlap);
    }

    /**
     * 切片文档
     */
    public List<TextSegment> split(Document document) {
        List<TextSegment> segments = splitter.split(document);
        log.info("[切片服务] 文档切片完成: {}个片段", segments.size());
        return segments;
    }

    /**
     * 切片纯文本
     */
    public List<TextSegment> splitText(String text, Map<String, String> metadata) {
        Document doc = Document.from(text, Metadata.from(metadata));
        return split(doc);
    }
}
