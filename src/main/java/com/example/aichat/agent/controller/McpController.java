package com.example.aichat.agent.controller;

import com.example.aichat.agent.service.McpGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpGatewayService mcpGateway;

    // 预置支付宝 MCP 服务地址
    private static final String ALIPAY_MCP_DEFAULT = "https://bailian.aliyuncs.com/mcp/alipay/sse";

    public McpController(McpGatewayService mcpGateway) {
        this.mcpGateway = mcpGateway;
    }

    /**
     * 列出 MCP 服务器上的工具
     */
    @PostMapping("/tools")
    public Map<String, Object> listTools(@RequestBody Map<String, String> body) {
        String serverUrl = body.getOrDefault("serverUrl", ALIPAY_MCP_DEFAULT);
        log.info("[MCP] 获取工具列表: serverUrl={}", serverUrl);
        List<Map<String, Object>> tools = mcpGateway.listTools(serverUrl);
        return Map.of("tools", tools, "serverUrl", serverUrl);
    }

    /**
     * 调用 MCP 工具（SSE 流式）
     */
    @PostMapping(value = "/tools/call", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter callTool(@RequestBody Map<String, Object> body) {
        String serverUrl = (String) body.getOrDefault("serverUrl", ALIPAY_MCP_DEFAULT);
        String toolName = (String) body.get("toolName");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) body.getOrDefault("arguments", Map.of());

        log.info("[MCP] 调用工具: serverUrl={}, toolName={}", serverUrl, toolName);
        return mcpGateway.callToolStream(serverUrl, toolName, arguments);
    }

    /**
     * 返回预置 MCP 服务列表
     */
    @GetMapping("/presets")
    public List<Map<String, Object>> listPresets() {
        return List.of(
            Map.of("name", "支付宝AI支付", "description", "创建/查询/退款支付宝订单", "serverUrl", ALIPAY_MCP_DEFAULT,
                   "tools", List.of(
                       Map.of("name", "create-alipay-payment", "description", "创建支付宝支付订单",
                              "parameters", List.of(
                                  Map.of("name", "subject", "type", "string", "required", true, "description", "商品名称"),
                                  Map.of("name", "amount", "type", "string", "required", true, "description", "支付金额 (元)")
                              )),
                       Map.of("name", "query-alipay-payment", "description", "查询支付宝订单状态",
                              "parameters", List.of(
                                  Map.of("name", "out_trade_no", "type", "string", "required", true, "description", "商户订单号")
                              )),
                       Map.of("name", "refund-alipay-payment", "description", "发起支付宝退款",
                              "parameters", List.of(
                                  Map.of("name", "out_trade_no", "type", "string", "required", true, "description", "商户订单号"),
                                  Map.of("name", "refund_amount", "type", "string", "required", true, "description", "退款金额 (元)")
                              ))
                   )
            )
        );
    }
}
