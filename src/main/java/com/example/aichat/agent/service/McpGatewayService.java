package com.example.aichat.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class McpGatewayService {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayService.class);
    private static final String PROTOCOL_VERSION = "0.1.0";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);

    /**
     * 向 MCP 服务器发送 JSON-RPC 请求，返回 SSE 流式响应
     */
    public SseEmitter callToolStream(String serverUrl, String toolName, Map<String, Object> arguments) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Thread.startVirtualThread(() -> {
            try {
                String sessionId = initialize(serverUrl);
                if (sessionId == null) {
                    emitter.send(SseEmitter.event().data(Map.of("type", "error", "message", "初始化 MCP 连接失败")));
                    emitter.complete();
                    return;
                }
                emitter.send(SseEmitter.event().data(Map.of("type", "log", "message", "MCP 会话已建立: " + sessionId)));

                Object result = callTool(serverUrl, sessionId, toolName, arguments);
                emitter.send(SseEmitter.event().data(Map.of("type", "result", "data", result != null ? result : Map.of())));
                emitter.complete();
            } catch (Exception e) {
                log.error("[MCP] 工具调用失败: toolName={}", toolName, e);
                try {
                    emitter.send(SseEmitter.event().data(Map.of("type", "error", "message", e.getMessage())));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });
        return emitter;
    }

    /**
     * 列出 MCP 服务器上的所有可用工具
     */
    public List<Map<String, Object>> listTools(String serverUrl) {
        try {
            String sessionId = initialize(serverUrl);
            if (sessionId == null) return List.of();
            return listToolsInternal(serverUrl, sessionId);
        } catch (Exception e) {
            log.error("[MCP] 获取工具列表失败: serverUrl={}", serverUrl, e);
            return List.of();
        }
    }

    private String initialize(String serverUrl) throws IOException {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", requestId.getAndIncrement());
        req.put("method", "initialize");
        ObjectNode params = req.putObject("params");
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.putObject("capabilities");
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "ai-chat-gateway");
        clientInfo.put("version", "1.0.0");

        JsonNode resp = sendMcpRequest(serverUrl, req);
        if (resp != null && resp.has("result")) {
            JsonNode result = resp.get("result");
            // Optional: send "notifications/initialized"
            return result.has("sessionId") ? result.get("sessionId").asText() : "default";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listToolsInternal(String serverUrl, String sessionId) throws IOException {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", requestId.getAndIncrement());
        req.put("method", "tools/list");
        req.putObject("params");

        JsonNode resp = sendMcpRequest(serverUrl, req);
        if (resp != null && resp.has("result") && resp.get("result").has("tools")) {
            return mapper.convertValue(resp.get("result").get("tools"), List.class);
        }
        return List.of();
    }

    private Object callTool(String serverUrl, String sessionId, String toolName, Map<String, Object> arguments) throws IOException {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", requestId.getAndIncrement());
        req.put("method", "tools/call");
        ObjectNode params = req.putObject("params");
        params.put("name", toolName);
        params.set("arguments", mapper.valueToTree(arguments != null ? arguments : Map.of()));

        JsonNode resp = sendMcpRequest(serverUrl, req);
        if (resp != null && resp.has("result")) {
            return mapper.treeToValue(resp.get("result"), Object.class);
        }
        if (resp != null && resp.has("error")) {
            return Map.of("error", resp.get("error").toPrettyString());
        }
        return null;
    }

    private JsonNode sendMcpRequest(String serverUrl, ObjectNode request) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(request), headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(serverUrl, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("[MCP] 请求失败: serverUrl={}, method={}", serverUrl,
                    request.has("method") ? request.get("method").asText() : "unknown", e);
            return null;
        }

        String body = response.getBody();
        if (body == null) return null;

        // 如果是 SSE 流，解析第一行 data:
        if (body.startsWith("data:")) {
            for (String line : body.split("\n")) {
                if (line.startsWith("data:")) {
                    String json = line.substring(5).trim();
                    if (!json.isBlank()) {
                        return mapper.readTree(json);
                    }
                }
            }
        }

        // 普通 JSON 响应
        return mapper.readTree(body);
    }
}
