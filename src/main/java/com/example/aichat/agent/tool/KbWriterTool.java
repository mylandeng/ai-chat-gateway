package com.example.aichat.agent.tool;

import com.example.aichat.rag.model.KnowledgeDocument;
import com.example.aichat.rag.repository.KnowledgeDocumentRepository;
import com.example.aichat.rag.service.ChunkService;
import com.example.aichat.rag.service.VectorStoreService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KbWriterTool {

    private static final Logger log = LoggerFactory.getLogger(KbWriterTool.class);

    private final ChunkService chunkService;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeDocumentRepository documentRepo;

    public KbWriterTool(ChunkService chunkService,
                        VectorStoreService vectorStoreService,
                        KnowledgeDocumentRepository documentRepo) {
        this.chunkService = chunkService;
        this.vectorStoreService = vectorStoreService;
        this.documentRepo = documentRepo;
    }

    @Tool("将文本内容写入指定的知识库，自动切片和向量化。输入知识库ID、文档名称和内容。")
    public String writeToKb(
            @P("知识库ID号") Long kbId,
            @P("文档名称，如 AI新闻汇总-2026-04-06") String docName,
            @P("要写入知识库的文本内容") String content) {

        if (kbId == null || kbId <= 0) {
            return "[知识库写入失败] 知识库ID不合法";
        }
        if (content == null || content.isBlank()) {
            return "[知识库写入失败] 内容不能为空";
        }

        log.info("[KbWriter] kbId={}, docName={}, contentLength={}", kbId, docName, content.length());

        try {
            // 1. 创建文档记录
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setTenantId(1L); // 默认租户，工作流场景
            doc.setKbId(kbId);
            doc.setFileName(docName != null ? docName : "workflow-doc-" + System.currentTimeMillis());
            doc.setCharCount(content.length());
            doc.setContentType("text/plain");
            doc.setFileSize((long) content.getBytes().length);
            doc.setStatus(1); // parsed
            doc = documentRepo.save(doc);

            // 2. 构建 metadata（用于向量检索过滤）
            Map<String, String> metadata = new HashMap<>();
            metadata.put("doc_id", String.valueOf(doc.getId()));
            metadata.put("tenant_id", String.valueOf(doc.getTenantId()));
            metadata.put("kb_id", String.valueOf(kbId));
            metadata.put("file_name", doc.getFileName());

            // 3. 文本切片
            List<TextSegment> segments = chunkService.splitText(content, metadata);
            doc.setChunkCount(segments.size());
            documentRepo.save(doc);

            // 4. 向量化并存入 PgVector
            List<String> embeddingIds = vectorStoreService.storeAll(segments);

            // 5. 标记完成
            doc.setStatus(2); // vectorized
            documentRepo.save(doc);

            log.info("[KbWriter] 写入成功: docId={}, chunks={}, embeddings={}",
                    doc.getId(), segments.size(), embeddingIds.size());

            return String.format("已写入知识库(ID=%d): 文档「%s」, %d字, 切分为%d个片段, 已向量化入库",
                    kbId, doc.getFileName(), content.length(), segments.size());

        } catch (Exception e) {
            log.error("[KbWriter] 写入失败: {}", e.getMessage(), e);
            return "[知识库写入失败] " + e.getMessage();
        }
    }
}
