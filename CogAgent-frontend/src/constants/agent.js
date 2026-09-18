/**
 * Agent 能力元数据
 *
 * ⚠️ 这里是前后端契约的唯一落点，改后端枚举时只改本文件。
 */

/**
 * Agent 运行模式（对应后端 cn.edu.ai.api.dto.enums.AgentMode）
 *
 * 【关键】后端 ChatRequest.mode 是 Java 枚举类型，Spring 默认使用 Jackson 按
 * 枚举的 name() 反序列化，因此 value 必须是大写枚举名（REACT / PLANNER / ...），
 * 而不是 AgentMode.code 里的 'react'。传小写会得到
 * HttpMessageNotReadableException → HTTP 400。
 *
 * 若后端日后给 AgentMode 加 @JsonCreator 改走 fromCode()，只需把下面的 value
 * 全部改成小写即可，其余代码无需改动。
 */
export const AGENT_MODES = [
  {
    value: 'REACT',
    label: 'ReAct',
    cn: '推理—行动',
    scene: '需求不明确的探索型任务，边想边调工具',
    intents: ['KNOWLEDGE_QA', 'TOOL_USE'],
    consumesTools: true,
  },
  {
    value: 'PLANNER',
    label: 'Planner',
    cn: '规划—执行',
    scene: '需求明确的多步任务，先出计划再逐步执行',
    intents: ['COMPLEX_TASK'],
    consumesTools: true,
  },
  {
    value: 'REFLECTION',
    label: 'Reflection',
    cn: '自我反思',
    scene: '对准确性要求高，先回答再自评并改进',
    intents: ['REASONING'],
    consumesTools: false,
  },
  {
    value: 'DIRECT',
    label: 'Direct',
    cn: '直接对话',
    scene: '问候、闲聊等简单对话，不经过编排',
    intents: ['SIMPLE_CHAT'],
    consumesTools: false,
  },
]

/** 「自动路由」不发送 mode 字段，交由后端 IntentRecognizer 决定 */
export const AUTO_MODE = {
  value: '',
  label: '自动路由',
  cn: '由意图识别决定',
  scene: '规则优先 + LLM 兜底，命中后自动映射到合适的模式',
  intents: [],
  consumesTools: true,
}

export const MODE_OPTIONS = [AUTO_MODE, ...AGENT_MODES]

/** 按 value 取模式元数据，未知值回落到自动路由 */
export function findMode(value) {
  return MODE_OPTIONS.find((m) => m.value === value) || AUTO_MODE
}

/** 意图 → 模式 的映射，与后端 IntentRecognizer 的约定一致，仅用于界面说明 */
export const INTENT_TO_MODE = {
  SIMPLE_CHAT: 'DIRECT',
  KNOWLEDGE_QA: 'REACT',
  TOOL_USE: 'REACT',
  COMPLEX_TASK: 'PLANNER',
  REASONING: 'REFLECTION',
}

export const INTENT_LABELS = {
  SIMPLE_CHAT: '简单闲聊',
  KNOWLEDGE_QA: '知识问答',
  TOOL_USE: '工具调用',
  COMPLEX_TASK: '复杂任务',
  REASONING: '推理分析',
}

/**
 * 内置工具（对应后端 BaseTool 实现类的 getName()）
 * 说明取自各工具的 javadoc / README 描述，均为只读操作。
 */
export const TOOLS = [
  {
    value: 'calculator',
    label: '计算器',
    hint: '数学表达式求值，JavaScript 引擎 + 四则运算兜底',
    access: '只读',
  },
  {
    value: 'search',
    label: '搜索',
    hint: '搜索引擎检索（当前后端为模拟实现，生产需对接真实 API）',
    access: '只读',
  },
  {
    value: 'database_query',
    label: '数据库查询',
    hint: '仅允许 SELECT，自动追加 LIMIT，禁止写操作',
    access: '只读',
  },
]

export const ALL_TOOL_VALUES = TOOLS.map((t) => t.value)

/**
 * 一次请求的完整处理链路，取自后端 AgentOrchestrator.chat() 的真实执行顺序。
 * 这是界面首页的主视觉依据 —— 顺序是产品事实，不是装饰性编号。
 */
export const REQUEST_PIPELINE = [
  { name: '记忆召回', impl: 'Redis 短期 + Milvus 长期', note: '滑动窗口 20 轮，TTL 60 分钟' },
  { name: '意图识别', impl: '规则优先 + LLM 兜底', note: '命中后映射到对应 Agent 模式' },
  { name: 'RAG 检索', impl: '向量召回 + LLM 精排', note: '相似度阈值 0.5，精排取 Top 3' },
  { name: '上下文合并', impl: '记忆上下文 + 知识库上下文', note: '拼成单条 Prompt 交给 Agent' },
  { name: 'Agent 执行', impl: 'ReAct / Planner / Reflection / Direct', note: '产出回答与思考步骤' },
  { name: '记忆更新', impl: '写回短期记忆，必要时归档', note: '超阈值触发长期记忆摘要' },
  { name: '链路追踪', impl: 'traceId + 多个 Span', note: '贯穿本次请求的每个阶段' },
]

/** 后端依赖的中间件，用于「服务依赖」清单展示 */
export const SERVICE_DEPS = [
  { name: 'MySQL', role: '业务数据持久化', key: 'spring.datasource' },
  { name: 'Redis', role: '短期记忆、缓存', key: 'spring.data.redis' },
  { name: 'Milvus', role: '文档分块与长期记忆向量库', key: 'milvus' },
  { name: 'DeepSeek', role: '对话模型（主力 pro / 备用 flash）', key: 'spring.ai.openai' },
  { name: 'DashScope', role: 'Embedding 模型 text-embedding-v3', key: 'embedding' },
]
