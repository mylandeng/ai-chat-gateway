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

-- W3: 知识文档表
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
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
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
