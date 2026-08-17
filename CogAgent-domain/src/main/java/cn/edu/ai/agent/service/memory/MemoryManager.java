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
     * 构建 LLM 请求所需的消息上下文
     * 组装顺序：系统提示 + 历史对话
     *
     * @param conversationId 会话 ID
     * @param systemPrompt   系统提示
     * @return 消息列表
     */
    public List<Message> buildContext(String conversationId, String systemPrompt) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        List<String> history = shortTermMemory.getRecentHistory(conversationId, maxTurns);
        for(String entry : history) {
            int separator = entry.indexOf(":");
            if (separator<=0) continue;
            String role = entry.substring(0, separator);
            String content = entry.substring(separator + 1);
            if("user".equals(role)) {
                messages.add(new UserMessage(entry));
            }
            else if ("assistant".equals(role)) {
                messages.add(new AssistantMessage(content));
            }
        }

        return messages;
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
