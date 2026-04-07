package com.example.aichat.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    @Value("${agent.tools.web-search.api-key:}")
    private String apiKey;

    @Value("${agent.tools.web-search.base-url:https://google.serper.dev/search}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Tool("搜索互联网获取最新信息。输入搜索关键词，返回相关网页标题和摘要。")
    @SuppressWarnings("unchecked")
    public String webSearch(@P("搜索关键词") String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return "[搜索不可用] 未配置搜索API密钥";
        }

        log.info("[WebSearch] query={}", query);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", apiKey);

            Map<String, Object> body = Map.of("q", query, "num", 5, "hl", "zh-cn");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl, entity, Map.class);
            List<Map<String, Object>> organic = (List<Map<String, Object>>) response.getBody().get("organic");

            if (organic == null || organic.isEmpty()) {
                return "未找到相关搜索结果";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(organic.size(), 5); i++) {
                Map<String, Object> item = organic.get(i);
                sb.append(String.format("%d. %s\n   %s\n   链接: %s\n\n",
                        i + 1,
                        item.getOrDefault("title", ""),
                        item.getOrDefault("snippet", ""),
                        item.getOrDefault("link", "")));
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("[WebSearch] 搜索失败: {}", e.getMessage());
            return "[搜索失败] " + e.getMessage();
        }
    }
}
