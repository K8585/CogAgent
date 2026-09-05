# CogAgent

> 企业级 AI Agent 平台 · 基于 Spring Boot + Spring AI

CogAgent 是一个模块化、可扩展的 AI Agent 平台，融合了多模式 Agent 编排、意图识别、RAG 检索增强、长短时记忆、工具调用、模型路由熔断降级、全链路追踪以及文档 ETL 数据管道等能力，帮助你快速构建生产级的智能对话与知识库应用。

---

## 核心特性


| 能力               | 说明                                                                                 |
| ------------------ | ------------------------------------------------------------------------------------ |
| **多模式 Agent**   | 内置 ReAct、Planner（Plan-and-Execute）、Reflection（自我反思）、Direct 四种执行模式 |
| **意图识别**       | 规则优先 + LLM 兜底的双层意图识别，自动路由到合适的 Agent 模式                       |
| **RAG 检索增强**   | 向量检索（Milvus / 内存）+ LLM 精排重排序，为回答提供权威知识上下文                  |
| **长短时记忆**     | 短期记忆基于 Redis（滑动窗口），长期记忆基于 Milvus 向量化摘要自动归档与召回         |
| **工具调用**       | 可插拔工具框架（BaseTool），内置计算器、搜索、数据库查询工具                         |
| **模型路由与熔断** | 主力/备用双模型自动切换，三态熔断器（CLOSED/OPEN/HALF_OPEN）保障可用性               |
| **文档 ETL 管道**  | 上传文档自动完成 解析 → 切片 → 向量化 → 入库 全流程                               |
| **全链路追踪**     | 为每次请求生成唯一 traceId，记录 Agent 执行的每一个 Span                             |

---

## 技术栈

- **JDK 17**
- **Spring Boot 3.5.0** / **Spring AI 1.0.0**
- **MyBatis Plus 3.5.7** + MySQL
- **Redis**（短期记忆、缓存）
- **Milvus 2.4.3**（向量数据库：文档分块、长期记忆）
- **Apache Tika 2.9.2**（多格式文档解析）
- **Hutool 5.8.28** / Lombok

模型接入：

- **对话模型**：DeepSeek（主力 `deepseek-v4-pro` / 备用 `deepseek-v4-flash`），通过 Spring AI 的 OpenAI 兼容接口接入
- **Embedding 模型**：通义千问 `text-embedding-v3`（通过阿里云 DashScope 的 OpenAI 兼容接口）

---

## 系统架构

项目采用多模块 Maven 架构，分层清晰、依赖单向：

```
┌──────────────────────────────────────────────────────────┐
│                     CogAgent-app                          │
│               启动类 + 配置文件（application.yml）          │
└──────────────────────────┬───────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────┐
│                   CogAgent-trigger                        │
│              HTTP 接口层（Controller）                     │
│         ChatController / DocumentController               │
└──────┬────────────────────────────────────────┬───────────┘
       │                                        │
┌──────▼───────────────┐            ┌───────────▼───────────┐
│    CogAgent-domain   │            │   CogAgent-api        │
│  核心业务逻辑层       │            │  接口 DTO 定义         │
│  agent/document/     │◄───────────│  ChatRequest 等        │
│  memory/rag/tool     │            └───────────────────────┘
└──────┬───────────────┘
       │
┌──────▼────────────────────────────────────────────────────┐
│              CogAgent-infrastructure                       │
│  config / cache / llm / trace / vectordb                   │
└──────┬────────────────────────────────────────────────────┘
       │
┌──────▼────────────────────────────────────────────────────┐
│                  CogAgent-types                           │
│          通用类型（Result / GlobalExceptionHandler）        │
└───────────────────────────────────────────────────────────┘
```

### 模块职责


| 模块                      | 职责                                                          |
| ------------------------- | ------------------------------------------------------------- |
| `CogAgent-app`            | 应用启动类、`application.yml` 全局配置                        |
| `CogAgent-trigger`        | 对外 HTTP 接口（对话、文档上传）                              |
| `CogAgent-domain`         | 核心业务：Agent 编排、意图识别、RAG、记忆、文档 ETL、工具     |
| `CogAgent-api`            | 接口层 DTO 定义（请求 / 响应 / 枚举）                         |
| `CogAgent-infrastructure` | 基础设施：模型配置、模型路由、熔断器、Milvus、Redis、链路追踪 |
| `CogAgent-types`          | 通用返回结构`Result`、全局异常处理                            |

---

## 一次请求的处理流程

```
用户消息
   │
   ▼
[1] 记忆召回        ── 短期记忆（Redis）+ 长期记忆（Milvus）
   ▼
[2] 意图识别        ── 规则命中 / LLM 兜底
   ▼
[3] RAG 检索        ── 向量检索 → LLM 重排序（可选）
   ▼
[4] 上下文合并      ── 记忆上下文 + 知识库上下文
   ▼
[5] Agent 执行      ── ReAct / Planner / Reflection / Direct
   ▼
[6] 记忆更新        ── 保存对话 + 触发长期记忆归档
   ▼
[7] 全链路追踪      ── 记录每个 Span（traceId）
```

---

## Agent 编排（4 种模式）

Agent 模式由 `AgentMode` 枚举定义，请求可显式指定，否则由意图识别自动路由。


| 模式           | 说明                                                                    | 适用场景             |
| -------------- | ----------------------------------------------------------------------- | -------------------- |
| **REACT**      | Reasoning + Acting 循环：思考 → 调用工具 → 观察结果，直至给出最终答案 | 需求不明确的任务     |
| **PLANNER**    | 先生成完整执行计划，再逐步执行各子任务，最后汇总综合                    | 需求明确的任务       |
| **REFLECTION** | 初始回答后自我评分、反思不足、改进回答（评分 ≥8 达标）                 | 对准确性要求高的场景 |
| **DIRECT**     | 直接对话，不经过编排                                                    | 简单闲聊、问候       |

> 所有模式均通过 `AgentOrchestrator` 统一调度，返回结构包含 `thinkingSteps`（思考过程）与 `usedTools`（工具调用记录），方便追踪 Agent 行为。

---

## 意图识别

`IntentRecognizer` 采用「规则优先 + LLM 兜底」的双层策略：

- **规则层**：快速匹配常见意图（问候、计算、搜索、数据库查询），低延迟、零成本
- **LLM 层**：规则未命中时，由 LLM 判断意图类别（`SIMPLE_CHAT` / `KNOWLEDGE_QA` / `TOOL_USE` / `COMPLEX_TASK` / `REASONING`）

意图类别与 Agent 模式映射：


| 意图           | Agent 模式 |
| -------------- | ---------- |
| `SIMPLE_CHAT`  | Direct     |
| `KNOWLEDGE_QA` | ReAct      |
| `TOOL_USE`     | ReAct      |
| `COMPLEX_TASK` | Planner    |
| `REASONING`    | Reflection |

---

## RAG 检索增强

- **多路检索 `MultiRetriever`**：向量语义检索（Milvus 或内存存储），按相似度阈值过滤
- **重排序 `Reranker`**：候选结果超过阈值时，通过 LLM 逐个评分（0-10）并取 Top-N 精排
- **文档切片 `DocumentChunker`**：按段落边界切片，带重叠以保持上下文连续性（默认 512 字符 / 重叠 64）

---

## 记忆系统


| 类型         | 存储       | 说明                                                                |
| ------------ | ---------- | ------------------------------------------------------------------- |
| **短期记忆** | Redis List | 维护单次会话近期对话，滑动窗口淘汰（默认保留 20 轮，TTL 60 分钟）   |
| **长期记忆** | Milvus     | 对话轮次超过阈值后，LLM 生成摘要并向量化归档，支持按 query 向量召回 |

---

## 工具系统

工具框架基于抽象基类 `BaseTool`，新工具只需继承并实现 `getName` / `getDescription` / `getParameterSchema` / `doExecute` / `validate` 五个方法即可自动注册。

内置工具：


| 工具       | 名称             | 说明                                                         |
| ---------- | ---------------- | ------------------------------------------------------------ |
| 计算器     | `calculator`     | 数学表达式求值（JavaScript 引擎 + 四则运算兜底）             |
| 搜索       | `search`         | 模拟搜索引擎（生产环境需对接真实搜索 API）                   |
| 数据库查询 | `database_query` | 仅允许`SELECT` 查询，自动加 `LIMIT`，禁止写操作防止 SQL 注入 |

> `ToolRegistry` 负责工具注册与查询，`ToolRouter` 负责将 LLM 输出的 JSON 参数路由到具体工具执行。

---

## 模型路由与熔断降级

`ModelRouter` 管理主力（`deepseek-v4-pro`）与备用（`deepseek-v4-flash`）两个模型，结合三态熔断器 `CircuitBreaker`：

```
CLOSED（正常）──连续失败≥阈值──▶ OPEN（熔断降级）
   ▲                                │ 超时
   │                                ▼
   └──连续成功≥阈值── HALF_OPEN（半开试探）
```

当主模型失败或熔断开启时，自动降级到备用模型，保证服务可用性。

---

## 文档 ETL 管道

`ETLPipeline` 负责文档从上传到入库的完整流程：

```
上传文件 → Extract（Tika 解析提取文本）→ Transform（切片为 Chunk）
        → Load（向量化 → 存入 Milvus 或内存）
```

支持 PDF、DOCX、TXT、HTML、Markdown 等多种格式。

---

## 项目结构

```
CogAgent
├── CogAgent-api/             # 接口 DTO 定义
│   └── src/main/java/cn/edu/ai/api/dto/
├── CogAgent-app/             # 启动类 + 配置
│   └── src/main/resources/application.yml
├── CogAgent-domain/          # 核心业务逻辑
│   └── src/main/java/cn/edu/ai/agent/service/
│       ├── agent/            #   Agent 编排（Orchestrator / ReAct / Planner / Reflection）
│       ├── document/         #   文档 ETL（Parser / Chunker / Pipeline）
│       ├── memory/           #   记忆（MemoryManager / Short / Long）
│       ├── rag/              #   RAG（MultiRetriever / Reranker）
│       └── tool/             #   工具（BaseTool / Registry / Router / impl）
├── CogAgent-infrastructure/  # 基础设施
│   └── src/main/java/cn/edu/ai/infrastructure/
│       ├── config/           #   模型 / Milvus / Redis 配置
│       ├── llm/              #   模型路由 / 熔断器
│       ├── trace/            #   链路追踪
│       └── vectordb/         #   Milvus / 内存向量存储
├── CogAgent-trigger/         # HTTP 接口层
│   └── src/main/java/cn/edu/ai/trigger/http/
├── CogAgent-types/           # 通用类型
│   └── src/main/java/cn/edu/ai/types/
└── pom.xml                   # 父 POM（依赖管理）
```

---
