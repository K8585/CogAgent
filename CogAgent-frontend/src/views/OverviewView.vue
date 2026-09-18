<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '@/api/client'
import { useConnection } from '@/composables/useConnection'
import {
  AGENT_MODES,
  INTENT_LABELS,
  REQUEST_PIPELINE,
  SERVICE_DEPS,
  TOOLS,
} from '@/constants/agent'
import { formatClockSec, formatMs } from '@/utils/format'

const router = useRouter()
const { connection, verify } = useConnection()

const connectionView = computed(() => {
  const c = connection.value
  if (c.status === 'offline') return { dot: 'var(--rust)', text: '未连接', tone: 'error' }
  // 服务连得上，但依赖组件异常。与「未连接」分开表达：排查方向完全不同
  if (c.status === 'degraded') return { dot: 'var(--ochre)', text: '依赖异常', tone: 'warn' }
  if (c.status === 'online' && !c.verified) return { dot: 'var(--ochre)', text: '响应异常', tone: 'warn' }
  if (c.status === 'online') return { dot: 'var(--celadon)', text: '已连接', tone: 'ok' }
  return { dot: 'var(--ink-4)', text: c.status === 'checking' ? '检测中' : '待检测', tone: 'idle' }
})

/** 与 ConnectionBadge 保持一致：探测说明跟随实际使用的路径 */
const probeMeta = computed(() =>
  connection.value.via === 'chain'
    ? { method: 'POST /api/v1/chat · message:""', expect: 'HTTP 400 · 参数校验失败' }
    : { method: 'GET /actuator/health', expect: 'HTTP 200 · {"status":"UP"}' }
)

/**
 * 后端地址：留空时说明走的是同源代理
 *
 * 注意：import.meta 不能在模板表达式里直接出现（Vue 的模板编译器只按表达式解析，
 * 拿不到 ESM 的 import.meta），必须先在这里取出来再交给模板。
 */
const apiBaseLabel = computed(() => API_BASE || '同源 / 经 Vite 代理转发')
const proxyTargetLabel = import.meta.env.VITE_PROXY_TARGET || '（生产构建未启用代理）'
const buildModeLabel = import.meta.env.DEV ? 'development' : 'production'

/** 点击模式行 → 跳转到对话页并预选该模式 */
function useMode(value) {
  router.push({ name: 'chat', query: { mode: value } })
}

function pad(n) {
  return String(n).padStart(2, '0')
}
</script>

<template>
  <div class="page">
    <!-- ———————————————— 页头 ———————————————— -->
    <header class="page__head">
      <h1>平台概览</h1>
      <p>
        CogAgent 把一次对话拆成七个可观测的阶段。下面这条链路是后端
        <code class="mono">AgentOrchestrator.chat()</code> 的真实执行顺序——回答为什么慢、
        为什么没检索到知识、工具为什么没被调用，答案都在这七步里。
      </p>
    </header>

    <!-- ———————————————— 主视觉：处理链路 ———————————————— -->
    <section class="block">
      <ol class="pipeline">
        <li v-for="(step, i) in REQUEST_PIPELINE" :key="step.name" class="pipeline__step">
          <div class="pipeline__rail">
            <span class="pipeline__index mono">{{ pad(i + 1) }}</span>
            <span v-if="i < REQUEST_PIPELINE.length - 1" class="pipeline__line" aria-hidden="true" />
          </div>
          <h3 class="pipeline__name">{{ step.name }}</h3>
          <p class="pipeline__impl mono">{{ step.impl }}</p>
          <p class="pipeline__note">{{ step.note }}</p>
        </li>
      </ol>
    </section>

    <!-- ———————————————— 模式 + 运行状态 ———————————————— -->
    <div class="grid grid--modes">
      <section class="panel">
        <div class="panel__head">
          <h2 class="panel__title">Agent 编排模式</h2>
          <span class="panel__note">点任意一行可直接在对话页选用</span>
        </div>

        <table class="table">
          <thead>
            <tr>
              <th>模式</th>
              <th>适用场景</th>
              <th>由哪些意图路由而来</th>
              <th class="col-tight">工具</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="mode in AGENT_MODES" :key="mode.value" class="mode-row" @click="useMode(mode.value)">
              <td>
                <span class="mode-name mono">{{ mode.label }}</span>
                <span class="mode-cn">{{ mode.cn }}</span>
              </td>
              <td class="mode-scene">{{ mode.scene }}</td>
              <td>
                <span v-for="intent in mode.intents" :key="intent" class="tag tag--mono">
                  {{ INTENT_LABELS[intent] || intent }}
                </span>
              </td>
              <td class="col-tight">
                <span v-if="mode.consumesTools" class="mode-tools">可调用</span>
                <span v-else class="mode-tools mode-tools--off">忽略</span>
              </td>
            </tr>
          </tbody>
        </table>

        <p class="panel-foot">
          未指定模式时，请求体不携带 <code class="mono">mode</code> 字段，由后端的规则优先 +
          LLM 兜底双层意图识别自动路由；显式选定模式则会跳过该判断。
        </p>
      </section>

      <section class="panel">
        <div class="panel__head">
          <h2 class="panel__title">运行状态</h2>
          <button
            type="button"
            class="btn btn--sm btn--quiet"
            :disabled="connection.status === 'checking'"
            @click="verify"
          >
            完整校验
          </button>
        </div>

        <div class="panel__body status">
          <div class="status__line">
            <span class="status__dot" :style="{ background: connectionView.dot }" />
            <strong>{{ connectionView.text }}</strong>
            <span v-if="connection.latencyMs !== null" class="mono status__latency">
              {{ formatMs(connection.latencyMs) }}
            </span>
          </div>

          <p class="status__detail">{{ connection.detail }}</p>

          <dl class="status__meta">
            <div>
              <dt>探测方式</dt>
              <dd class="mono">{{ probeMeta.method }}</dd>
            </div>
            <div>
              <dt>预期响应</dt>
              <dd class="mono">{{ probeMeta.expect }}</dd>
            </div>
            <div>
              <dt>接口基址</dt>
              <dd class="mono">{{ apiBaseLabel }}</dd>
            </div>
            <div>
              <dt>代理目标</dt>
              <dd class="mono">{{ proxyTargetLabel }}</dd>
            </div>
            <div>
              <dt>构建模式</dt>
              <dd class="mono">{{ buildModeLabel }}</dd>
            </div>
            <div v-if="connection.checkedAt">
              <dt>最近探测</dt>
              <dd class="mono">{{ formatClockSec(connection.checkedAt) }}</dd>
            </div>
          </dl>

          <div v-if="connection.status === 'offline'" class="notice notice--error">
            后端未响应。确认 Spring Boot 应用已启动在 8080 端口，再点「完整校验」。
          </div>
          <div v-else-if="connection.status === 'degraded'" class="notice notice--warn">
            后端可达，但健康检查报了异常——通常是 MySQL / Redis / Milvus 连不上。
            此时对话与文档上传大概率会失败，先查中间件，再查后端。
          </div>
          <div v-else-if="connection.status === 'online' && !connection.verified" class="notice notice--warn">
            端口上有服务在监听，但探测响应与 CogAgent 的预期不符——可能是别的服务占用了 8080，
            或后端接口有变更。
          </div>
        </div>
      </section>
    </div>

    <!-- ———————————————— 工具 + 依赖 ———————————————— -->
    <div class="grid grid--even">
      <section class="panel">
        <div class="panel__head">
          <h2 class="panel__title">内置工具</h2>
          <span class="panel__note">继承 BaseTool 即自动注册</span>
        </div>
        <ul class="deflist">
          <li v-for="tool in TOOLS" :key="tool.value" class="deflist__item">
            <div class="deflist__key">
              <span class="mono">{{ tool.value }}</span>
              <span class="tag tag--muted">{{ tool.access }}</span>
            </div>
            <div class="deflist__val">
              <strong>{{ tool.label }}</strong>
              <span>{{ tool.hint }}</span>
            </div>
          </li>
        </ul>
        <p class="panel-foot">
          工具选择只在 ReAct 与 Planner 下生效；Reflection 与 Direct 会忽略
          <code class="mono">tools</code> 字段。
        </p>
      </section>

      <section class="panel">
        <div class="panel__head">
          <h2 class="panel__title">服务依赖</h2>
          <span class="panel__note">缺任一项，对应能力会降级</span>
        </div>
        <ul class="deflist">
          <li v-for="dep in SERVICE_DEPS" :key="dep.name" class="deflist__item">
            <div class="deflist__key">
              <span class="mono">{{ dep.name }}</span>
            </div>
            <div class="deflist__val">
              <strong>{{ dep.role }}</strong>
              <span class="mono deflist__cfg">{{ dep.key }}</span>
            </div>
          </li>
        </ul>
        <p class="panel-foot">
          全部地址都可用环境变量覆盖（<code class="mono">REDIS_HOST</code>、
          <code class="mono">MILVUS_HOST</code> 等），默认指向 localhost。
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 30px 26px 56px;
}

.page > * + * {
  margin-top: 26px;
}

.page__head {
  max-width: 78ch;
}

.page__head h1 {
  font-size: var(--fs-3xl);
  letter-spacing: -0.02em;
  margin-bottom: 10px;
}

.page__head p {
  color: var(--ink-2);
  line-height: 1.75;
}

.page__head code,
.panel-foot code {
  padding: 1px 4px;
  border-radius: var(--radius-xs);
  background: var(--surface-sunk);
  font-size: 0.92em;
}

/* ———————————————— 处理链路 ———————————————— */
.pipeline {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  padding: 20px 18px 18px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--surface);
}

.pipeline__step {
  padding-right: 16px;
}

.pipeline__step:last-child {
  padding-right: 0;
}

.pipeline__rail {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 14px;
  margin-bottom: 12px;
}

.pipeline__index {
  flex: none;
  color: var(--celadon);
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
}

.pipeline__line {
  flex: 1;
  min-width: 0;
  height: 1px;
  background: var(--rule-strong);
}

.pipeline__name {
  font-size: var(--fs-lg);
  margin-bottom: 5px;
}

.pipeline__impl {
  font-size: var(--fs-xs);
  color: var(--celadon-deep);
  line-height: 1.55;
  margin-bottom: 3px;
}

.pipeline__note {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  line-height: 1.6;
}

/* ———————————————— 栅格 ———————————————— */
.grid {
  display: grid;
  gap: 20px;
  align-items: start;
}

.grid--modes {
  grid-template-columns: minmax(0, 1.72fr) minmax(0, 1fr);
}

.grid--even {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

/* ———————————————— 模式表 ———————————————— */
.mode-row {
  cursor: pointer;
}

.mode-name {
  display: block;
  font-weight: 600;
  color: var(--celadon-deep);
}

.mode-cn {
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.mode-scene {
  color: var(--ink-2);
  line-height: 1.6;
}

.mode-tools {
  font-size: var(--fs-sm);
  color: var(--celadon);
}

.mode-tools--off {
  color: var(--ink-4);
}

.table td .tag + .tag {
  margin-left: 4px;
}

.panel-foot {
  padding: 11px 18px 13px;
  border-top: 1px solid var(--rule);
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--ink-3);
}

/* ———————————————— 运行状态 ———————————————— */
.status__line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.status__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: none;
}

.status__latency {
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.status__detail {
  font-size: var(--fs-sm);
  line-height: 1.68;
  color: var(--ink-2);
  word-break: break-word;
  margin-bottom: 14px;
}

.status__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--rule);
}

.status__meta > div {
  display: flex;
  gap: 10px;
}

.status__meta dt {
  flex: none;
  width: 68px;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.status__meta dd {
  margin: 0;
  font-size: var(--fs-xs);
  color: var(--ink-2);
  word-break: break-all;
}

.status .notice {
  margin-top: 14px;
}

/* ———————————————— 定义列表 ———————————————— */
.deflist__item {
  display: flex;
  gap: 16px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--rule);
}

.deflist__item:last-child {
  border-bottom: none;
}

.deflist__key {
  flex: none;
  width: 104px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  font-size: var(--fs-sm);
  color: var(--ink);
}

.deflist__val {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.deflist__val strong {
  font-size: var(--fs-md);
  font-weight: 500;
}

.deflist__val span {
  font-size: var(--fs-sm);
  color: var(--ink-3);
  line-height: 1.6;
}

.deflist__cfg {
  font-size: var(--fs-xs) !important;
}

/* 中窄屏：链路改为纵向单列，用左侧竖线维持「顺序」的语义 */
@media (max-width: 1080px) {
  .pipeline {
    grid-template-columns: 1fr;
  }
  .pipeline__step {
    padding: 0 0 14px;
    border-left: 1px solid var(--rule-strong);
    padding-left: 16px;
    margin-left: 3px;
  }
  .pipeline__step:last-child {
    border-left-color: transparent;
    padding-bottom: 0;
  }
  .pipeline__line {
    display: none;
  }
  .pipeline__rail {
    margin-bottom: 6px;
  }
  .grid--modes,
  .grid--even {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .page {
    padding: 22px 14px 44px;
  }
  .page__head h1 {
    font-size: var(--fs-2xl);
  }
  .deflist__item {
    flex-direction: column;
    gap: 6px;
  }
  .deflist__key {
    width: auto;
    flex-direction: row;
    align-items: center;
    gap: 8px;
  }
}
</style>
