-- 租户表
CREATE TABLE IF NOT EXISTS tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '租户名称',
    contact_email VARCHAR(200) COMMENT '联系邮箱',
    status INT DEFAULT 1 COMMENT '1启用 0禁用',
    monthly_quota BIGINT DEFAULT 1000000 COMMENT '月配额(tokens)',
    daily_quota BIGINT DEFAULT 100000 COMMENT '日配额(tokens)',
    monthly_used BIGINT DEFAULT 0 COMMENT '月已用',
    daily_used BIGINT DEFAULT 0 COMMENT '日已用',
    quota_reset_day INT DEFAULT 1 COMMENT '月配额重置日',
    last_daily_reset DATE COMMENT '上次日配额重置时间',
    last_monthly_reset DATE COMMENT '上次月配额重置时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 默认租户
INSERT IGNORE INTO tenant (id, name, status) VALUES (1, 'Default Tenant', 1);

-- API Key 表
CREATE TABLE IF NOT EXISTS api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_id VARCHAR(32) NOT NULL UNIQUE COMMENT '展示用ID: ak-xxxx',
    key_secret VARCHAR(128) NOT NULL COMMENT '加密后的密钥',
    key_prefix VARCHAR(10) NOT NULL COMMENT '明文前缀，用于展示: sk-abc***',
    name VARCHAR(100) COMMENT 'Key 名称（用户自定义）',
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    rate_limit INT DEFAULT 60 COMMENT '每分钟最大请求数',
    allowed_models VARCHAR(500) COMMENT '允许使用的模型列表，逗号分隔，空=全部',
    expires_at DATETIME COMMENT '过期时间，null=永不过期',
    last_used_at DATETIME COMMENT '最后使用时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status),
    INDEX idx_prefix_status (key_prefix, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- API 调用日志表
CREATE TABLE IF NOT EXISTS api_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_id VARCHAR(32) NOT NULL,
    tenant_id BIGINT NOT NULL,
    model VARCHAR(50) NOT NULL,
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    duration_ms INT COMMENT '响应耗时(ms)',
    status VARCHAR(20) COMMENT 'success/error',
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_key_time (key_id, created_at),
    INDEX idx_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(36) PRIMARY KEY COMMENT 'UUID',
    tenant_id BIGINT NOT NULL,
    title VARCHAR(200) COMMENT '会话标题',
    model VARCHAR(50) NOT NULL COMMENT '使用的模型',
    system_prompt TEXT COMMENT '系统提示词',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'system/user/assistant',
    content TEXT NOT NULL,
    token_count INT DEFAULT 0 COMMENT '该条消息的 token 数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W3: 知识文档表 (W4: 增加 kb_id 字段)
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    kb_id BIGINT COMMENT '所属知识库（W4新增）',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(500) COMMENT '存储路径',
    file_size BIGINT COMMENT '文件大小(bytes)',
    content_type VARCHAR(100) COMMENT 'MIME类型',
    char_count INT DEFAULT 0 COMMENT '提取的字符数',
    chunk_count INT DEFAULT 0 COMMENT '切片数量',
    status INT DEFAULT 0 COMMENT '0上传中 1解析完成 2已向量化 -1失败',
    error_message VARCHAR(1000) COMMENT '失败原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_kb (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W4: 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    name VARCHAR(200) NOT NULL COMMENT '知识库名称',
    description VARCHAR(500) COMMENT '描述',
    doc_count INT DEFAULT 0 COMMENT '文档数量',
    visibility VARCHAR(20) DEFAULT 'private' COMMENT 'private/shared',
    share_token VARCHAR(64) COMMENT '分享令牌',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    UNIQUE INDEX idx_share_token (share_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W4: RAG 对话会话表
CREATE TABLE IF NOT EXISTS rag_chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id BIGINT NOT NULL COMMENT '所属知识库',
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    title VARCHAR(200) COMMENT '会话标题（首条问题截取）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_tenant (kb_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W4: RAG 对话消息表
CREATE TABLE IF NOT EXISTS rag_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '所属会话',
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant',
    content TEXT NOT NULL COMMENT '消息内容',
    rewritten_query VARCHAR(500) COMMENT '改写后的查询（仅 user 消息）',
    sources TEXT COMMENT '引用来源 JSON（仅 assistant 消息）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W5: Agent 定义表
CREATE TABLE IF NOT EXISTS agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    name VARCHAR(100) NOT NULL COMMENT 'Agent名称',
    description VARCHAR(500) COMMENT '描述',
    avatar VARCHAR(50) COMMENT '图标(emoji)',
    system_prompt TEXT NOT NULL COMMENT '系统提示词',
    model_id VARCHAR(50) NOT NULL DEFAULT 'deepseek-chat' COMMENT '使用的模型ID',
    tools_config JSON COMMENT '启用的工具列表 ["web_search","knowledge_base:3"]',
    is_template TINYINT DEFAULT 0 COMMENT '1=预设模板 0=用户自建',
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_template (is_template)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W5: Agent 对话会话表
CREATE TABLE IF NOT EXISTS agent_chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL COMMENT '所属Agent',
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    title VARCHAR(200) COMMENT '会话标题',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agent_tenant (agent_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W5: Agent 对话消息表（含工具调用记录）
CREATE TABLE IF NOT EXISTS agent_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '所属会话',
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant/tool',
    content TEXT COMMENT '消息内容',
    tool_calls JSON COMMENT 'assistant消息中的工具调用请求',
    tool_name VARCHAR(50) COMMENT 'tool消息: 工具名称',
    tool_input TEXT COMMENT 'tool消息: 工具输入参数',
    tool_output TEXT COMMENT 'tool消息: 工具返回结果',
    tool_duration_ms INT COMMENT 'tool消息: 执行耗时',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- W5: 工作流定义表
CREATE TABLE IF NOT EXISTS agent_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    name VARCHAR(100) NOT NULL COMMENT '工作流名称',
    description VARCHAR(500) COMMENT '描述',
    steps JSON NOT NULL COMMENT '步骤定义 [{stepNo,toolName,inputTemplate,description}]',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 提示词模板表
CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) COMMENT '描述',
    category VARCHAR(50) COMMENT '分类',
    content TEXT NOT NULL COMMENT '模板内容',
    variables JSON COMMENT '变量定义',
    version INT DEFAULT 1 COMMENT '当前版本号',
    is_public TINYINT(1) DEFAULT 0 COMMENT '是否公开',
    status INT DEFAULT 1 COMMENT '1启用 0禁用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 提示词模板版本表
CREATE TABLE IF NOT EXISTS prompt_template_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL COMMENT '所属模板',
    version INT NOT NULL COMMENT '版本号',
    content TEXT NOT NULL COMMENT '版本内容',
    variables JSON COMMENT '变量定义',
    change_note VARCHAR(500) COMMENT '变更说明',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 模板收藏表
CREATE TABLE IF NOT EXISTS template_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_template (tenant_id, template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
