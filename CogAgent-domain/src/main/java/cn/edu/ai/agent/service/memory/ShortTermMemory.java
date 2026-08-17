package cn.edu.ai.agent.service.memory;

import cn.edu.ai.infrastructure.cache.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 短期记忆
 * 基于 Redis List 存储近期对话历史，自动滑动窗口淘汰。
 * 适用于维持单次会话上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortTermMemory {

    private final RedisCacheService cacheService;

    @Value("${agent.memory.short-term.max-turns:20}")
    private int maxTurns;
    @Value("${agent.memory.short-term.ttl-minutes:60}")
    private int ttlMinutes;

    private static final String KEY_PREFIX = "memory:short:";

    /**
     * 添加一条消息到对话历史
     *
     * @param conversationId 会话 ID
     * @param role           消息角色（user / assistant）
     * @param content        消息内容
     */
    public void addMessage(String conversationId, String role, String content) {
        String key = KEY_PREFIX + conversationId;
        String entry = role + ":" + content;

        cacheService.listRightPush(key, entry);
        cacheService.listTrim(key, -maxTurns * 2L, -1);
        cacheService.expire(key, Duration.ofMinutes(ttlMinutes));

        log.debug("短期记忆写入: conversationId={}, role={}", conversationId, role);
    }

    /**
     * 获取会话的完整对话历史
     */
    public List<String> getHistory(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        List<String> history = cacheService.listRange(key, 0, -1);
        return history != null ? history : new ArrayList<>();
    }

    /**
     * 获取最近 N 轮对话
     */
    public List<String> getRecentHistory(String conversationId, int turns) {
        String key = KEY_PREFIX + conversationId;
        List<String> history = cacheService.listRange(key, -turns * 2L, -1);
        return history != null ? history : new ArrayList<>();
    }

    /**
     * 清除会话的短期记忆
     */
    public void clear(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        cacheService.delete(key);
        log.info("清除短期记忆: conversationId={}", conversationId);
    }
}
