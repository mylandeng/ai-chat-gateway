package com.example.aichat.service;

import com.example.aichat.model.entity.ChatMessage;
import com.example.aichat.repository.ChatMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationManager.class);

    private final StringRedisTemplate redisTemplate;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CACHE_PREFIX = "chat:session:";
    private static final int CACHE_TTL_HOURS = 2;

    public ConversationManager(StringRedisTemplate redisTemplate,
                                ChatMessageRepository messageRepository) {
        this.redisTemplate = redisTemplate;
        this.messageRepository = messageRepository;
    }

    /**
     * 添加消息到对话
     */
    public void addMessage(String sessionId, String role, String content) {
        log.debug("[对话] 添加消息 sessionId={}, role={}, content长度={}", sessionId, role, content != null ? content.length() : 0);

        // 持久化到 MySQL
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokenCount(estimateTokens(content));
        try {
            messageRepository.save(msg);
        } catch (Exception e) {
            log.error("[对话] MySQL保存失败 sessionId={}, role={}", sessionId, role, e);
            throw e;
        }

        // 追加到 Redis 缓存
        try {
            String key = CACHE_PREFIX + sessionId;
            String msgJson = toJson(Map.of("role", role, "content", content));
            redisTemplate.opsForList().rightPush(key, msgJson);
            redisTemplate.expire(key, Duration.ofHours(CACHE_TTL_HOURS));
        } catch (Exception e) {
            log.warn("[对话] Redis缓存写入失败 sessionId={}, 不影响主流程", sessionId, e);
        }
    }

    /**
     * 获取对话历史（用于发送给LLM）
     */
    public List<Map<String, String>> getMessages(String sessionId, int maxTokens) {
        String key = CACHE_PREFIX + sessionId;

        List<String> cached = null;
        try {
            cached = redisTemplate.opsForList().range(key, 0, -1);
        } catch (Exception e) {
            log.warn("[对话] Redis读取失败 sessionId={}, 回退到MySQL", sessionId, e);
        }

        if (cached == null || cached.isEmpty()) {
            log.debug("[对话] Redis缓存未命中 sessionId={}, 从MySQL加载", sessionId);
            cached = loadFromDB(sessionId);
            if (!cached.isEmpty()) {
                try {
                    redisTemplate.opsForList().rightPushAll(key, cached);
                    redisTemplate.expire(key, Duration.ofHours(CACHE_TTL_HOURS));
                } catch (Exception e) {
                    log.warn("[对话] Redis回填缓存失败 sessionId={}", sessionId, e);
                }
            }
        } else {
            log.debug("[对话] Redis缓存命中 sessionId={}, 消息数={}", sessionId, cached.size());
        }

        List<Map<String, String>> messages = cached.stream()
            .map(this::fromJson)
            .collect(Collectors.toList());

        List<Map<String, String>> truncated = truncateMessages(messages, maxTokens);
        if (truncated.size() < messages.size()) {
            log.info("[对话] 消息裁剪 sessionId={}, 裁剪前={}, 裁剪后={}, maxTokens={}", sessionId, messages.size(), truncated.size(), maxTokens);
        }
        return truncated;
    }

    /**
     * 滑动窗口裁剪：保留 system prompt + 最新若干消息，总 token 不超过 maxTokens
     */
    public List<Map<String, String>> truncateMessages(
            List<Map<String, String>> messages, int maxTokens) {

        if (messages.isEmpty()) return messages;

        List<Map<String, String>> systemMsgs = new ArrayList<>();
        List<Map<String, String>> dialogMsgs = new ArrayList<>();

        for (var msg : messages) {
            if ("system".equals(msg.get("role"))) {
                systemMsgs.add(msg);
            } else {
                dialogMsgs.add(msg);
            }
        }

        int systemTokens = systemMsgs.stream()
            .mapToInt(m -> estimateTokens(m.get("content")))
            .sum();
        int remainingTokens = maxTokens - systemTokens;

        List<Map<String, String>> kept = new ArrayList<>();
        int usedTokens = 0;

        for (int i = dialogMsgs.size() - 1; i >= 0; i--) {
            var msg = dialogMsgs.get(i);
            int msgTokens = estimateTokens(msg.get("content"));

            if (usedTokens + msgTokens > remainingTokens) {
                break;
            }

            kept.add(0, msg);
            usedTokens += msgTokens;
        }

        List<Map<String, String>> result = new ArrayList<>();
        result.addAll(systemMsgs);
        result.addAll(kept);
        return result;
    }

    /**
     * 粗略估算 token 数
     * 中文约 1.5 字符/token，英文约 4 字符/token
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 2 + 10;
    }

    private List<String> loadFromDB(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
            .map(msg -> toJson(Map.of("role", msg.getRole(), "content", msg.getContent())))
            .toList();
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }
}
