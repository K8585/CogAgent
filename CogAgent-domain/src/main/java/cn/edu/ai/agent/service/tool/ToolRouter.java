package cn.edu.ai.agent.service.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具路由器
 * 将 LLM 输出的 JSON 格式的工具调用路由到实际的工具执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRouter {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 路由并执行工具调用
     *
     * @param toolName   工具名称
     * @param argsJson   参数 JSON 字符串
     * @return 执行结果
     */
    public BaseTool.ToolResult route(String toolName, String argsJson) {
        log.info("路由工具调用: tool={}, args={}", toolName, argsJson);

        BaseTool tool = toolRegistry.getTool(toolName);
        if (tool != null) {
            // 把json参数转化为map对象： {"city":"北京","days":3} -> {city=北京, days=3}
            Map<String, Object> parameters = parseParameters(argsJson);
            return tool.execute(parameters);
        } else {
            log.warn("未找到工具: {}", toolName);
            return BaseTool.ToolResult.failure(
                    toolName,
                    "工具 '" + toolName + "' 未注册，可用工具列表: " + toolRegistry.getAllTools(),
                    0
            );
        }

    }

    private Map<String, Object> parseParameters(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return Map.of();
        }
        try {
            // 把 JSON 转化为 Java 对象
            return objectMapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("工具参数 JSON 解析失败: {}", argsJson, e);
            throw new IllegalArgumentException("工具参数格式错误: " + e.getMessage());
        }
    }
}
