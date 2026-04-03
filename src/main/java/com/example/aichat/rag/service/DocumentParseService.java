package com.example.aichat.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Service
public class DocumentParseService {

    private final DocumentParser documentParser;

    public DocumentParseService() {
        // LangChain4j 的 Tika 解析器，支持几乎所有文档格式
        this.documentParser = new ApacheTikaDocumentParser();
    }

    /**
     * 解析上传的文件
     */
    public Document parse(MultipartFile file) throws IOException {
        // LangChain4j 的 Document 对象包含文本和元数据
        Document document = documentParser.parse(file.getInputStream());

        // 补充元数据
        document.metadata()
                .put("file_name", file.getOriginalFilename())
                .put("file_size", String.valueOf(file.getSize()))
                .put("content_type", file.getContentType())
                .put("upload_time", LocalDateTime.now().toString());

        return document;
    }

    /**
     * 解析本地文件
     */
    public Document parseFile(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath)) {
            Document document = documentParser.parse(is);
            document.metadata()
                    .put("file_name", filePath.getFileName().toString())
                    .put("file_path", filePath.toString());
            return document;
        }
    }
}
