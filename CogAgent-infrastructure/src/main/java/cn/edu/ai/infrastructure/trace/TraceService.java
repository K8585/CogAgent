package cn.edu.ai.infrastructure.trace;

import io.lettuce.core.tracing.TraceContext;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链路追踪服务
 * 为每个请求生成唯一 traceId，记录 Agent 执行全链路日志，
 * 便于调试、性能分析和问题排查
 */
@Slf4j
@Service
public class TraceService {

    private static final String TRACE_ID_KEY = "traceId";
    // 存储活跃的追踪上下文
    private final Map<String, TraceContext> activeTraces = new ConcurrentHashMap<>();

    /**
     * 开始一个新的追踪
     */
    public String startTrace(String operation){
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // 把 traceId 绑定到当前线程，让后续所有的日志都能自动带上这个ID
        MDC.put(TRACE_ID_KEY, traceId);

        TraceContext context = TraceContext.builder()
                .traceId(traceId)
                .operation(operation)
                .startTime(Instant.now())
                .spans(new ArrayList<>())
                .build();
        activeTraces.put(traceId, context);
        log.info("开始追踪: operation={}, traceId={}", operation, traceId);

        return traceId;
    }

    /**
     * 添加一个追踪节点（Span）
     */
    public void addSpan(String traceId, String spanName, Map<String, Object> attributes){
        TraceContext context = activeTraces.get(traceId);
        if (context == null) {
            log.warn("追踪上下文不存在: traceId={}", traceId);
            return;
        }

        TraceSpan span = TraceSpan.builder()
                .name(spanName)
                .timestamp(Instant.now())
                .attributes(attributes)
                .build();
        context.getSpans().add(span);

        log.debug("[Trace:{}] Span: {} | {}", traceId, spanName, attributes);
    }

    /**
     * 结束追踪
     */
    public TraceContext endTrace(String traceId){
        TraceContext traceContext = activeTraces.remove(traceId);
        if(traceContext != null){
            traceContext.setEndTime(Instant.now());
            traceContext.setDurationMs(traceContext.getEndTime().toEpochMilli() - traceContext.getStartTime().toEpochMilli());
            log.info("追踪结束: traceId={}, duration={}ms, spans={}",
                    traceId, traceContext.getDurationMs(), traceContext.getSpans().size());
        }
        MDC.remove(TRACE_ID_KEY);
        return traceContext;
    }

    /**
     * 获取当前追踪 ID
     */
    public String currentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    @Data
    @Builder
    public static class TraceContext {
        private String traceId;          // 唯一追踪ID
        private String operation;        // 操作名称
        private Instant startTime;       // 开始时间
        private Instant endTime;         // 结束时间
        private long durationMs;         // 总耗时（毫秒）
        private List<TraceSpan> spans;   // 所有步骤列表
    }

    @Data
    @Builder
    public static class TraceSpan {
        private String name;                    // 步骤名称
        private Instant timestamp;              // 步骤发生时间
        private Map<String, Object> attributes; // 附加信息
    }

}
