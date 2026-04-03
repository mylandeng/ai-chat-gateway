package com.example.aichat.service;

import com.example.aichat.model.dto.PromptTemplateRequest;
import com.example.aichat.repository.PromptTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class TemplateInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TemplateInitializer.class);

    private final PromptTemplateRepository templateRepository;
    private final PromptTemplateService templateService;

    public TemplateInitializer(PromptTemplateRepository templateRepository,
                                PromptTemplateService templateService) {
        this.templateRepository = templateRepository;
        this.templateService = templateService;
    }

    @Override
    public void run(String... args) {
        if (templateRepository.count() > 0) {
            log.debug("[模板初始化] 已有模板，跳过");
            return;
        }

        log.info("[模板初始化] 创建预置公开模板...");

        create("通用翻译", "translation",
            "将内容准确翻译成目标语言，保持原文格式和语气",
            "你是一个专业翻译。请将以下内容翻译成{{targetLang}}，保持原文格式和语气：\n\n{{content}}",
            "[{\"name\":\"targetLang\",\"label\":\"目标语言\",\"required\":true,\"default\":\"中文\"},{\"name\":\"content\",\"label\":\"待翻译内容\",\"required\":true}]");

        create("代码审查", "coding",
            "审查代码的安全性、性能和规范问题",
            "请审查以下{{language}}代码，重点关注：\n1. 安全漏洞\n2. 性能问题\n3. 代码规范\n4. 可能的 Bug\n\n请给出具体的改进建议。\n\n```{{language}}\n{{code}}\n```",
            "[{\"name\":\"language\",\"label\":\"编程语言\",\"required\":true,\"default\":\"Java\"},{\"name\":\"code\",\"label\":\"代码内容\",\"required\":true}]");

        create("文章摘要", "analysis",
            "提取文章核心内容和关键词",
            "请阅读以下文章并提供：\n1. 100字以内的摘要\n2. 3-5个关键词\n3. 核心观点列表\n\n文章内容：\n{{article}}",
            "[{\"name\":\"article\",\"label\":\"文章内容\",\"required\":true}]");

        create("邮件撰写", "writing",
            "根据要点生成正式的商务邮件",
            "请帮我写一封{{tone}}的邮件，主题是{{subject}}。\n\n关键要点：\n{{points}}\n\n收件人：{{recipient}}",
            "[{\"name\":\"tone\",\"label\":\"语气\",\"required\":true,\"default\":\"正式\"},{\"name\":\"subject\",\"label\":\"邮件主题\",\"required\":true},{\"name\":\"points\",\"label\":\"关键要点\",\"required\":true},{\"name\":\"recipient\",\"label\":\"收件人称呼\",\"required\":true}]");

        create("SQL 生成", "coding",
            "根据自然语言描述生成 SQL 查询",
            "你是一个 SQL 专家。根据以下表结构和需求，生成{{dbType}} SQL 查询语句。\n\n表结构：\n{{schema}}\n\n需求：{{requirement}}",
            "[{\"name\":\"dbType\",\"label\":\"数据库类型\",\"required\":true,\"default\":\"MySQL\"},{\"name\":\"schema\",\"label\":\"表结构\",\"required\":true},{\"name\":\"requirement\",\"label\":\"查询需求\",\"required\":true}]");

        log.info("[模板初始化] 完成，创建了 5 个预置模板");
    }

    private void create(String name, String category, String description, String content, String variables) {
        templateService.create(0L, new PromptTemplateRequest(
            name, description, category, content, variables, true, null));
    }
}
