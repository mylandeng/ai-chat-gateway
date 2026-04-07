package com.example.aichat.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

@Component
public class CodeInterpreterTool {

    private static final Logger log = LoggerFactory.getLogger(CodeInterpreterTool.class);

    @Tool("执行简单的数学表达式或JavaScript代码片段，返回计算结果。仅支持简单计算。")
    public String executeCode(@P("数学表达式或简单JS代码") String code) {
        log.info("[CodeInterpreter] code={}", code);
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("js");
            if (engine == null) {
                engine = manager.getEngineByName("nashorn");
            }
            if (engine == null) {
                return "[代码执行不可用] 未找到JavaScript引擎，请安装GraalJS";
            }
            Object result = engine.eval(code);
            return result != null ? result.toString() : "null";
        } catch (Exception e) {
            log.warn("[CodeInterpreter] 执行失败: {}", e.getMessage());
            return "[执行错误] " + e.getMessage();
        }
    }
}
