package com.example.aichat.agent.tool;

import com.example.aichat.service.ChatModelFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LlmSummarizeTool {

    private static final Logger log = LoggerFactory.getLogger(LlmSummarizeTool.class);

    private final ChatModelFactory chatModelFactory;

    @Value("${agent.tools.llm-summarize.model-id:deepseek-chat}")
    private String modelId;

    public LlmSummarizeTool(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    @Tool("使用AI大模型对文本内容进行总结、分析、改写或翻译。输入需要处理的提示词和内容。")
    public String summarize(@P("需要AI处理的提示词和内容") String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "[AI总结] 输入内容为空";
        }

        log.info("[LlmSummarize] modelId={}, prompt length={}", modelId, prompt.length());
        try {
            ChatLanguageModel model = chatModelFactory.getModel(modelId);
            String result = model.generate(prompt);
            log.info("[LlmSummarize] 完成, result length={}", result.length());
            return result;
        } catch (Exception e) {
            log.error("[LlmSummarize] 失败: {}", e.getMessage(), e);
            return "[AI总结失败] " + e.getMessage();
        }
    }
}
