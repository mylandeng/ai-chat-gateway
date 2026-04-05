package com.example.aichat.rag.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @description: 自定义中文切片器
 * @version 2.0
 * @project ai-chat-gateway
 * @class: ChineseDocumentSplitter
 * @author: linhaibo
 * @date: 2026/4/4 23:37
 */
public class ChineseDocumentSplitter implements DocumentSplitter {

    private final int chunkSize;
    private final int overlap;

    // 中文分隔符优先级
    private static final List<String> SEPARATORS = List.of(
        "\n\n",     // 段落
        "\n",       // 换行
        "。",       // 中文句号
        "！",       // 中文感叹号
        "？",       // 中文问号
        "；",       // 中文分号
        ". ",       // 英文句号
        "! ",       // 英文感叹号
        "? ",       // 英文问号
        "，",       // 中文逗号
        ", ",       // 英文逗号
        " ",        // 空格
        ""          // 逐字符
    );

    public ChineseDocumentSplitter(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<TextSegment> split(Document document) {
        List<String> chunks = splitText(document.text(), 0);
        List<TextSegment> segments = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Metadata metadata = Metadata.from(document.metadata().toMap());
            metadata.put("chunk_index", String.valueOf(i));
            metadata.put("chunk_total", String.valueOf(chunks.size()));
            segments.add(TextSegment.from(chunks.get(i).trim(), metadata));
        }

        return segments;
    }

    private List<String> splitText(String text, int separatorIndex) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        if (separatorIndex >= SEPARATORS.size()) {
            // 最后手段：硬切
            return hardSplit(text);
        }

        String separator = SEPARATORS.get(separatorIndex);
        String[] parts = separator.isEmpty()
            ? text.split("") : text.split(Pattern.quote(separator));

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            String candidate = current.length() > 0
                ? current + separator + part : part;

            if (candidate.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString());
                // overlap: 保留 current 末尾一部分
                String overlapText = getOverlap(current.toString());
                current = new StringBuilder(overlapText + separator + part);
            } else {
                current = new StringBuilder(candidate);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        // 对仍然超大的 chunk，用下一级分隔符继续切
        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.length() > chunkSize) {
                result.addAll(splitText(chunk, separatorIndex + 1));
            } else {
                result.add(chunk);
            }
        }

        return result;
    }

    private String getOverlap(String text) {
        if (text.length() <= overlap) return text;
        return text.substring(text.length() - overlap);
    }

    private List<String> hardSplit(String text) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize - overlap) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
        }
        return chunks;
    }
}