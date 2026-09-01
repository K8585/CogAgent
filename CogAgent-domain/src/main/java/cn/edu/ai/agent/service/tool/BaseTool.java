package cn.edu.ai.agent.service.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 工具基类
 * 所有自定义工具必须继承此类
 * 基类提供统一的执行入口、异常处理、耗时记录
 */
@Slf4j
public abstract class BaseTool {

    /**
     * 获取工具名称（唯一标识）
     */
    public abstract String getName();

    /**
     * 获取工具描述（供 LLM 理解工具用途）
     */
    public abstract String getDescription();

    /**
     * 获取参数 JSON Schema 描述
     */
    public abstract String getParameterSchema();

    /**
     * 工具执行入口（模板方法）
     * 负责统一的日志记录、耗时计算、异常捕获
     */
    public final ToolResult execute(Map<String, Object> parameters) {
        long startTime = System.currentTimeMillis();
        log.info("开始执行工具: name={}, params={}", getName(), parameters);

        try {
            validate(parameters);
            String result = doExecute(parameters);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("工具执行成功: name={}, elapsed={}ms", getName(), elapsed);
            return ToolResult.success(getName(), result, elapsed);
        } catch (IllegalArgumentException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("工具参数校验失败: name={}, error={}", getName(), e.getMessage());
            return ToolResult.failure(getName(), "参数错误: " + e.getMessage(), elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("工具执行失败: name={}", getName(), e);
            return ToolResult.failure(getName(), "执行失败: " + e.getMessage(), elapsed);
        }
    }

    /**
     * 实际执行逻辑
     *
     * @param parameters 工具参数
     * @return 执行结果
     */
    protected abstract String doExecute(Map<String, Object> parameters);

    /**
     * 参数校验
     */
    protected abstract void validate(Map<String, Object> parameters);

    /**
     * 工具执行结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolResult {

        private String toolName;
        private Boolean success;
        private String output;
        private long elapsedMs;

        public static ToolResult success(String toolName, String output, long elapsedMs) {
            return ToolResult.builder()
                    .toolName(toolName)
                    .success(true)
                    .output(output)
                    .elapsedMs(elapsedMs)
                    .build();
        }

        public static ToolResult failure(String toolName, String error, long elapsedMs) {
            return ToolResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .output(error)
                    .elapsedMs(elapsedMs)
                    .build();
        }
    }
}
