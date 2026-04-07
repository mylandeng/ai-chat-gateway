package com.example.aichat.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UrlReaderTool {

    private static final Logger log = LoggerFactory.getLogger(UrlReaderTool.class);
    private static final int MAX_LENGTH = 4000;

    @Tool("读取网页内容并提取文本。输入完整的URL地址，返回网页的文本内容。")
    public String readUrl(@P("网页URL地址") String url) {
        log.info("[UrlReader] url={}", url);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            // 移除脚本和样式
            doc.select("script, style, nav, footer, header").remove();

            String title = doc.title();
            String text = doc.body().text();

            if (text.length() > MAX_LENGTH) {
                text = text.substring(0, MAX_LENGTH) + "... (内容已截断)";
            }

            return String.format("标题: %s\n\n%s", title, text);
        } catch (Exception e) {
            log.warn("[UrlReader] 读取失败: {} - {}", url, e.getMessage());
            return "[读取失败] " + e.getMessage();
        }
    }
}
