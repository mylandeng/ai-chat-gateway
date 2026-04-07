package com.example.aichat.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FileWriterTool {

    private static final Logger log = LoggerFactory.getLogger(FileWriterTool.class);

    @Value("${agent.tools.file-writer.output-dir:./workflow-output}")
    private String outputDir;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(outputDir));
            log.info("[FileWriter] 输出目录: {}", Paths.get(outputDir).toAbsolutePath());
        } catch (IOException e) {
            log.warn("[FileWriter] 创建输出目录失败: {}", e.getMessage());
        }
    }

    @Tool("将文本内容保存为本地文件。支持 .md、.txt、.json 等格式。")
    public String writeFile(
            @P("文件名，例如 news-2026-04-06.md") String fileName,
            @P("要保存的文件内容") String content) {

        if (fileName == null || fileName.isBlank()) {
            return "[文件写入失败] 文件名不能为空";
        }
        if (content == null || content.isBlank()) {
            return "[文件写入失败] 内容不能为空";
        }

        // 安全校验：防止路径穿越
        String sanitized = fileName.replace("..", "")
                .replace("/", "").replace("\\", "");
        if (sanitized.isBlank()) {
            return "[文件写入失败] 文件名不合法";
        }

        Path filePath = Paths.get(outputDir, sanitized);

        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            String absolutePath = filePath.toAbsolutePath().toString();
            log.info("[FileWriter] 文件已保存: {}, size={} bytes", absolutePath, content.length());
            return "文件已保存: " + absolutePath;
        } catch (IOException e) {
            log.error("[FileWriter] 写入失败: {}", e.getMessage(), e);
            return "[文件写入失败] " + e.getMessage();
        }
    }
}
