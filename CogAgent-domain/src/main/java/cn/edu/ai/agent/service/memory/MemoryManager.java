package cn.edu.ai.agent.service.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆管理器
 * 统一管理短期记忆和长期记忆，负责：
 *   写入消息到短期记忆
 *   构建 LLM 所需的消息上下文
 *   触发长期记忆的归档
 *   记忆的生命周期管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryManager {

    private final ShortTermMemory shortTermMemory;
    private final LongTermMemory longTermMemory;

    @Value("${agent.memory.long-term.enabled:true}")
    private boolean longTermEnabled;
    @Value("${agent.memory.short-term.max-turns:20}")
    private int maxTurns;
    @Value("${agent.memory.long-term.top-k:3}")
    private int longTermTopK;

    /**
     * 保存用户消息
     */
    public void saveUserMessage(String conversationId, String content) {
        shortTermMemory.addMessage(conversationId, "user", content);
    }

    /**
     * 保存助手回复
     */
    public void saveAssistantMessage(String conversationId, String content) {
        shortTermMemory.addMessage(conversationId, "assistant", content);
    }

    /**
     * 召回记忆上下文
     * 组装顺序：近期对话历史 + 长期记忆
     */
    public String recallMemoryContext(String conversationId, String query) {
        StringBuilder context = new StringBuilder();

        // 短期记忆召回：最近对话历史
        List<String> history = shortTermMemory.getRecentHistory(conversationId, maxTurns);
        if (!history.isEmpty()) {
            context.append("近期对话历史：\n");
            for (String entry : history) {
                int separator = entry.indexOf(":");
                if (separator <= 0) continue;
                String role = entry.substring(0, separator);
                String content = entry.substring(separator + 1);
                context.append("user".equals(role) ? "用户: " : "助手: ").append(content).append("\n");
            }
        }

        // 长期记忆召回：向量检索相关摘要
        if (longTermEnabled && history.size() >= maxTurns * 2) {
            try {
                List<String> memories = longTermMemory.recallByQuery(conversationId, query, longTermTopK);
                if (!memories.isEmpty()) {
                    context.append("\n相关长期记忆：\n");
                    for (String memory : memories) {
                        context.append("- ").append(memory).append("\n");
                    }
                }
            } catch (Exception e) {
                log.warn("长期记忆召回失败: conversationId={}", conversationId, e);
            }
        }

        return context.toString();
    }

    /**
     * 长期记忆归档
     * 当对话轮次超过阈值时，通过 LLM 生成摘要并存入向量数据库
     */
    public void archiveIfNeeded(String conversationId) {
        if(!longTermEnabled) return;

        List<String> history = shortTermMemory.getHistory(conversationId);
        // 未超过归档阈值，返回
        if (history.size() < maxTurns * 2) return;

        try {
            String summary = longTermMemory.generateSummary(history);
            longTermMemory.archive(conversationId, history, summary);
        } catch (Exception e) {
            log.error("长期记忆归档失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 清除短期记忆
     */
    public void clearMemory(String conversationId) {
        shortTermMemory.clear(conversationId);
    }

    /**
     * 获取会话的短期对话历史
     */
    public List<String> getHistory(String conversationId) {
        return shortTermMemory.getHistory(conversationId);
    }

}
