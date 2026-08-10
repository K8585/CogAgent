package cn.edu.ai.api.dto.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 运行模式枚举
 */
@Getter
@AllArgsConstructor
public enum AgentMode {

    REACT("react", "ReAct 推理-行动循环模式"),
    PLANNER("planner", "规划-执行模式"),
    REFLECTION("reflection", "自我反思改进模式"),
    DIRECT("direct", "直接对话模式");

    private final String code;
    private final String description;

    public static AgentMode fromCode(String code) {
        for (AgentMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {     // 不区分大小写
                return mode;
            }
        }
        return REACT;
    }
}
