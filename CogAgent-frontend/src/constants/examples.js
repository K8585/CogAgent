/**
 * 起始示例问题
 *
 * 这组问题是照着后端 IntentRecognizer 的五种意图逐条挑的，
 * 用来在一次测试里把四条 Agent 分支都跑一遍：
 *
 *   "你好"                     → SIMPLE_CHAT   → Direct
 *   "…等于多少"                → TOOL_USE      → ReAct + calculator
 *   "…是什么 / 为什么"          → KNOWLEDGE_QA  → ReAct + RAG 检索
 *   "帮我规划 / 分几步"         → COMPLEX_TASK  → Planner
 *   "对比 / 权衡 / 该选哪个"    → REASONING     → Reflection
 *
 * 意图由后端规则层与 LLM 层共同判定，不保证 100% 命中预期分支，
 * 但作为测试用例的覆盖面是够的。实际命中的模式可以在「运行观测」页对照查看。
 */
export const EXAMPLE_PROMPTS = [
  {
    label: '闲聊',
    expect: 'Direct · SIMPLE_CHAT',
    text: '你好，简单介绍一下你自己',
  },
  {
    label: '工具调用',
    expect: 'ReAct · calculator',
    text: '帮我算一下 (23 × 17 + 45) ÷ 8 等于多少',
  },
  {
    label: '知识问答',
    expect: 'ReAct · RAG 检索',
    text: '熔断器在连续失败多少次之后会进入熔断状态？进入后怎样恢复？',
  },
  {
    label: '多步任务',
    expect: 'Planner · COMPLEX_TASK',
    text: '帮我规划一个把现有知识库迁移到 Milvus 集群的三步实施方案',
  },
  {
    label: '推理权衡',
    expect: 'Reflection · REASONING',
    text: 'ReAct 和 Planner 两种编排模式各有什么取舍？什么情况下该选哪一个？',
  },
]
