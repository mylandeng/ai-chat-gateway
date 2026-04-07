package com.example.aichat.agent.service;

import com.example.aichat.agent.model.AgentWorkflow;
import com.example.aichat.agent.repository.AgentWorkflowRepository;
import com.example.aichat.agent.tool.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgentWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(AgentWorkflowService.class);

    private final AgentWorkflowRepository workflowRepo;
    private final WebSearchTool webSearchTool;
    private final KnowledgeBaseTool kbTool;
    private final UrlReaderTool urlReaderTool;
    private final CurrentTimeTool currentTimeTool;
    private final CodeInterpreterTool codeInterpreterTool;
    private final LlmSummarizeTool llmSummarizeTool;
    private final FileWriterTool fileWriterTool;
    private final KbWriterTool kbWriterTool;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AgentWorkflowService(AgentWorkflowRepository workflowRepo,
                                 WebSearchTool webSearchTool,
                                 KnowledgeBaseTool kbTool,
                                 UrlReaderTool urlReaderTool,
                                 CurrentTimeTool currentTimeTool,
                                 CodeInterpreterTool codeInterpreterTool,
                                 LlmSummarizeTool llmSummarizeTool,
                                 FileWriterTool fileWriterTool,
                                 KbWriterTool kbWriterTool) {
        this.workflowRepo = workflowRepo;
        this.webSearchTool = webSearchTool;
        this.kbTool = kbTool;
        this.urlReaderTool = urlReaderTool;
        this.currentTimeTool = currentTimeTool;
        this.codeInterpreterTool = codeInterpreterTool;
        this.llmSummarizeTool = llmSummarizeTool;
        this.fileWriterTool = fileWriterTool;
        this.kbWriterTool = kbWriterTool;
    }

    public List<AgentWorkflow> listByTenant(Long tenantId) {
        return workflowRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public AgentWorkflow create(Long tenantId, AgentWorkflow workflow) {
        workflow.setTenantId(tenantId);
        return workflowRepo.save(workflow);
    }

    public AgentWorkflow update(Long id, Long tenantId, AgentWorkflow updates) {
        AgentWorkflow wf = workflowRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("工作流不存在"));
        wf.setName(updates.getName());
        wf.setDescription(updates.getDescription());
        wf.setSteps(updates.getSteps());
        return workflowRepo.save(wf);
    }

    public void delete(Long id, Long tenantId) {
        workflowRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("工作流不存在"));
        workflowRepo.deleteById(id);
    }

    /**
     * 执行工作流，通过 SSE 逐步推送结果
     */
    public SseEmitter run(Long workflowId, String userInput) {
        SseEmitter emitter = new SseEmitter(300_000L);

        executor.submit(() -> {
            try {
                AgentWorkflow workflow = workflowRepo.findById(workflowId)
                        .orElseThrow(() -> new RuntimeException("工作流不存在"));

                List<Map<String, Object>> steps = objectMapper.readValue(
                        workflow.getSteps(), new TypeReference<>() {});

                Map<String, String> context = new HashMap<>();
                context.put("userInput", userInput);

                for (Map<String, Object> step : steps) {
                    int stepNo = ((Number) step.get("stepNo")).intValue();
                    String toolName = (String) step.get("toolName");
                    String inputTemplate = (String) step.get("inputTemplate");
                    String description = (String) step.getOrDefault("description", "");

                    // 发送步骤开始
                    emitter.send(SseEmitter.event().name("step_start")
                            .data(objectMapper.writeValueAsString(Map.of(
                                    "stepNo", stepNo,
                                    "toolName", toolName,
                                    "description", description))));

                    // 解析输入模板
                    String input = resolveTemplate(inputTemplate, context);

                    // 执行工具
                    long start = System.currentTimeMillis();
                    String output = executeTool(toolName, input);
                    long durationMs = System.currentTimeMillis() - start;

                    // 保存结果到上下文
                    context.put("step" + stepNo + "_output", output);

                    // 发送步骤结果
                    String displayOutput = output.length() > 1000 ?
                            output.substring(0, 1000) + "..." : output;
                    emitter.send(SseEmitter.event().name("step_end")
                            .data(objectMapper.writeValueAsString(Map.of(
                                    "stepNo", stepNo,
                                    "output", displayOutput,
                                    "durationMs", durationMs))));
                }

                emitter.send(SseEmitter.event()
                        .data(objectMapper.writeValueAsString(Map.of("type", "done"))));
                emitter.complete();

            } catch (Exception e) {
                log.error("[Workflow] 执行失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .data(objectMapper.writeValueAsString(
                                    Map.of("type", "error", "message", e.getMessage()))));
                } catch (IOException ignored) {}
                emitter.complete();
            }
        });

        return emitter;
    }

    private String resolveTemplate(String template, Map<String, String> context) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String executeTool(String toolName, String input) {
        return switch (toolName) {
            case "web_search" -> webSearchTool.webSearch(input);
            case "url_reader" -> urlReaderTool.readUrl(input);
            case "current_time" -> currentTimeTool.getCurrentTime();
            case "code_interpreter" -> codeInterpreterTool.executeCode(input);
            case "knowledge_base" -> {
                // 输入格式: "kbId:问题" 或直接问题（默认kbId=1）
                if (input.contains(":")) {
                    String[] parts = input.split(":", 2);
                    try {
                        yield kbTool.queryKnowledgeBase(parts[1].trim(), Long.parseLong(parts[0].trim()));
                    } catch (NumberFormatException e) {
                        yield kbTool.queryKnowledgeBase(input, 1L);
                    }
                } else {
                    yield kbTool.queryKnowledgeBase(input, 1L);
                }
            }
            case "llm_summarize" -> llmSummarizeTool.summarize(input);
            case "kb_writer" -> {
                // 输入格式: "kbId:docName\n内容" 或 "kbId\n内容"
                int nl = input.indexOf('\n');
                if (nl > 0) {
                    String header = input.substring(0, nl).trim();
                    String content = input.substring(nl + 1);
                    String[] parts = header.split(":", 2);
                    try {
                        Long kbId = Long.parseLong(parts[0].trim());
                        String docName = parts.length > 1 ? parts[1].trim() : "workflow-doc";
                        yield kbWriterTool.writeToKb(kbId, docName, content);
                    } catch (NumberFormatException e) {
                        yield "[知识库写入失败] 第一行格式应为 kbId:文档名，如 3:AI新闻汇总";
                    }
                } else {
                    yield "[知识库写入失败] 输入格式: 第一行 kbId:文档名，换行后为内容";
                }
            }
            case "file_writer" -> {
                // 输入格式: 第一行为文件名，其余为内容
                int newline = input.indexOf('\n');
                if (newline > 0) {
                    String fileName = input.substring(0, newline).trim();
                    String content = input.substring(newline + 1);
                    yield fileWriterTool.writeFile(fileName, content);
                } else {
                    // 没有换行时，用时间戳作文件名，整段作为内容
                    String fileName = "output-" + System.currentTimeMillis() + ".md";
                    yield fileWriterTool.writeFile(fileName, input);
                }
            }
            default -> "[未知工具] " + toolName;
        };
    }
}
