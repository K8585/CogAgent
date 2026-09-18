<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import TracePanel from './TracePanel.vue'
import SourceList from './SourceList.vue'
import { findMode } from '@/constants/agent'
import { renderMarkdown } from '@/utils/markdown'
import { formatClock, formatMs, formatNumber, shortId } from '@/utils/format'

/**
 * 单条消息
 *
 * 布局不用左右气泡，而是「左侧沟槽 + 内容」：
 * 整条消息流是一条自上而下的时间轴，沟槽里放角色与时间，轴上的节点区分角色。
 * 这与执行轨迹的纵向轴线是同一套语言 —— 整个界面在说同一件事：这是一条调用链。
 */
const props = defineProps({
  message: { type: Object, required: true },
  /** 是否是最后一条（决定流式光标只出现在这里） */
  isLast: { type: Boolean, default: false },
})

const isUser = computed(() => props.message.role === 'user')

const roleLabel = computed(() => (isUser.value ? '你' : 'Agent'))

/**
 * Markdown 渲染做了节流
 *
 * 流式输出时 content 每来一个 token 就变一次，若直接渲染，
 * 等于对不断增长的全文反复跑 marked + DOMPurify，
 * 一次长回答的总开销是 O(n²)，token 越多越卡。
 * 这里按 80ms 的节奏取样，人眼察觉不到延迟，渲染次数却降到几十分之一。
 * 拖尾执行保证最后一个 token 一定被渲染出来。
 */
const RENDER_INTERVAL = 80
const renderSource = ref(props.message.content)
let renderTimer = null

watch(
  () => props.message.content,
  () => {
    if (renderTimer) return
    renderTimer = setTimeout(() => {
      renderTimer = null
      renderSource.value = props.message.content
    }, RENDER_INTERVAL)
  }
)

onBeforeUnmount(() => {
  if (renderTimer) clearTimeout(renderTimer)
})

const html = computed(() => renderMarkdown(renderSource.value))

const isRunning = computed(() => props.message.status === 'running')

/** 轨迹面板只在同步接口下有意义：流式接口不经过编排，不会有步骤 */
const showTrace = computed(
  () => !isUser.value && !props.message.stream && (props.message.thinkingSteps?.length > 0)
)

const hasMeta = computed(
  () => !isUser.value && (props.message.model || props.message.tokenUsage || props.message.latencyMs !== null)
)

const modeLabel = computed(() => {
  const m = props.message.requestedMode
  return m ? findMode(m).label : '自动路由'
})
</script>

<template>
  <article class="msg" :class="[`msg--${message.role}`, { 'is-running': isRunning }]">
    <div class="msg__gutter">
      <span class="msg__node" aria-hidden="true" />
      <span class="msg__role">{{ roleLabel }}</span>
      <span class="msg__time mono">{{ formatClock(message.at) }}</span>
    </div>

    <div class="msg__body">
      <!-- —————— 用户消息：浅色块，与 Agent 的回答明确分开 —————— -->
      <div v-if="isUser" class="msg__input">{{ message.content }}</div>

      <!-- —————— Agent 回答 —————— -->
      <template v-else>
        <!-- 执行轨迹放在答案之前：这是本产品的性格，折叠态只占一行 -->
        <TracePanel
          v-if="showTrace"
          :steps="message.thinkingSteps"
          :used-tools="message.usedTools || []"
          :latency-ms="message.latencyMs"
        />

        <div v-if="message.content" class="md" v-html="html" />

        <p v-else-if="isRunning" class="msg__pending">
          <span class="msg__pulse" aria-hidden="true" />
          {{ message.stream ? '正在生成…' : '正在执行…' }}
        </p>

        <span v-if="isRunning && message.content" class="msg__caret" aria-hidden="true" />

        <!-- —————— 失败态：说清发生了什么，并给出可执行的下一步 —————— -->
        <div v-if="message.status === 'error'" class="notice notice--error msg__error">
          <div>
            <strong>{{ message.error || '请求失败' }}</strong>
            <span v-if="message.errorCode" class="mono msg__err-code">{{ message.errorCode }}</span>
            <p class="msg__err-hint">
              <template v-if="message.errorCode === 'NETWORK'">
                检查后端是否已在 8080 端口启动；若后端不在本机，改
                <code class="mono">.env.development</code> 里的
                <code class="mono">VITE_PROXY_TARGET</code> 后重启开发服务器。
              </template>
              <template v-else-if="message.errorCode === 'TIMEOUT'">
                ReAct / Planner 会多轮调用模型，遇到复杂问题耗时可能超过三分钟。
                后端日志里按 traceId 能查到它执行到哪一步了。
              </template>
              <template v-else-if="message.errorCode === 400">
                请求参数被后端拒绝。若刚切换过 Agent 模式，请确认模式值与后端 AgentMode 枚举一致。
              </template>
              <template v-else>
                后端日志里按 traceId 检索可看到完整堆栈。
              </template>
            </p>
          </div>
        </div>

        <p v-if="message.status === 'aborted'" class="msg__aborted">已停止生成。</p>

        <SourceList :sources="message.sources || []" />

        <!-- —————— 元信息：一行密集的事实，不做视觉装饰 —————— -->
        <div v-if="hasMeta" class="msg__meta">
          <span class="msg__meta-item">{{ modeLabel }}</span>

          <template v-if="message.model">
            <span class="msg__meta-sep">·</span>
            <span class="msg__meta-item mono">{{ message.model }}</span>
          </template>

          <template v-if="message.tokenUsage">
            <span class="msg__meta-sep">·</span>
            <span class="msg__meta-item mono" :title="`输入 ${message.tokenUsage.promptTokens} / 输出 ${message.tokenUsage.completionTokens}`">
              {{ formatNumber(message.tokenUsage.totalTokens) }} tok
            </span>
          </template>

          <template v-if="message.latencyMs !== null">
            <span class="msg__meta-sep">·</span>
            <span class="msg__meta-item mono">{{ formatMs(message.latencyMs) }}</span>
          </template>

          <template v-if="message.stream">
            <span class="msg__meta-sep">·</span>
            <span class="msg__meta-item">直通流式</span>
          </template>

          <template v-if="message.traceId">
            <span class="msg__meta-sep">·</span>
            <span class="msg__meta-item mono" :title="message.traceId">
              trace {{ shortId(message.traceId) }}
            </span>
          </template>
        </div>
      </template>
    </div>
  </article>
</template>

<style scoped>
.msg {
  display: flex;
  gap: 18px;
  padding: 16px 0;
}

/* —— 左侧沟槽：角色 + 时间，轴上的节点 —— */
.msg__gutter {
  position: relative;
  flex: none;
  width: 52px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  padding-top: 1px;
}

.msg__node {
  position: absolute;
  /*
    轴线画在 .thread__list 的 left:61px 处，也就是沟槽宽度 52 与间距 18 的中点。
    沟槽右边缘在 x=52，节点宽 7px，要让节点中心落在 x=61，
    则右边缘需在 x=64.5，相对沟槽右边缘的偏移即 -12.5px。
  */
  right: -12.5px;
  top: 4px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  border: 1px solid var(--ink-4);
  background: var(--paper);
}

.msg--assistant .msg__node {
  border-color: var(--celadon);
  background: var(--celadon);
}

.msg.is-running .msg__node {
  animation: node-pulse 1.6s var(--ease) infinite;
}

@keyframes node-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 var(--celadon-mist);
  }
  50% {
    box-shadow: 0 0 0 4px var(--celadon-mist);
  }
}

.msg__role {
  font-size: var(--fs-xs);
  color: var(--ink-2);
  font-weight: 500;
}

.msg__time {
  font-size: var(--fs-xs);
  color: var(--ink-4);
}

/* —— 内容区 —— */
.msg__body {
  flex: 1;
  min-width: 0;
  max-width: 76ch;
}

/* 用户输入：浅底块，一眼区分「我说的」和「它答的」 */
.msg__input {
  padding: 10px 13px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--surface);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

/* —— 生成中 —— */
.msg__pending {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-md);
  color: var(--ink-3);
}

.msg__pulse {
  width: 14px;
  height: 2px;
  background: var(--celadon);
  animation: bar-slide 1.1s var(--ease) infinite;
}

@keyframes bar-slide {
  0%,
  100% {
    opacity: 0.25;
    transform: translateX(-4px);
  }
  50% {
    opacity: 1;
    transform: translateX(4px);
  }
}

/* 流式追加时的光标 */
.msg__caret {
  display: inline-block;
  width: 7px;
  height: 14px;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--celadon);
  animation: caret-blink 1s steps(2, start) infinite;
}

@keyframes caret-blink {
  to {
    visibility: hidden;
  }
}

/* —— 失败提示 —— */
.msg__error {
  margin-top: 10px;
}

.msg__err-code {
  margin-left: 7px;
  font-size: var(--fs-xs);
  opacity: 0.75;
}

.msg__err-hint {
  margin-top: 5px;
  line-height: 1.65;
  opacity: 0.85;
}

.msg__err-hint code {
  padding: 1px 3px;
  border-radius: var(--radius-xs);
  background: rgba(0, 0, 0, 0.05);
}

.msg__aborted {
  margin-top: 8px;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

/* —— 元信息行 —— */
.msg__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--rule);
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.msg__meta-sep {
  color: var(--ink-4);
}
</style>

<!--
  Markdown 正文样式不写 scoped：
  v-html 注入的节点不带 scope 属性，scoped 选择器匹配不到它们。
  这里统一挂在 .md 下，作用域靠类名收敛，不会外泄到其它组件。
-->
<style>
.md {
  font-size: var(--fs-lg);
  line-height: var(--lh-body);
  color: var(--ink);
  word-break: break-word;
}

.md > * + * {
  margin-top: 12px;
}

.md h1,
.md h2,
.md h3,
.md h4 {
  margin-top: 20px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: -0.01em;
}

.md h1 {
  font-size: var(--fs-2xl);
}
.md h2 {
  font-size: var(--fs-xl);
}
.md h3 {
  font-size: var(--fs-lg);
}
.md h4 {
  font-size: var(--fs-md);
}

.md p {
  margin: 0;
}

.md ul,
.md ol {
  padding-left: 20px;
  list-style: revert;
}

.md li + li {
  margin-top: 4px;
}

.md li::marker {
  color: var(--ink-3);
}

.md a {
  text-decoration: underline;
  text-underline-offset: 3px;
  text-decoration-color: var(--rule-strong);
}

.md a:hover {
  text-decoration-color: var(--celadon);
}

.md code {
  padding: 1px 4px;
  border-radius: var(--radius-xs);
  background: var(--surface-sunk);
  font-family: var(--font-mono);
  font-size: 0.89em;
  color: var(--celadon-deep);
}

.md pre {
  padding: 11px 13px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--surface-sunk);
  overflow-x: auto;
}

.md pre code {
  padding: 0;
  background: none;
  color: var(--ink);
  font-size: var(--fs-sm);
  line-height: 1.65;
}

.md blockquote {
  padding: 2px 0 2px 14px;
  border-left: 2px solid var(--rule-strong);
  color: var(--ink-2);
}

.md table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-md);
}

.md th,
.md td {
  padding: 7px 10px;
  border: 1px solid var(--rule);
  text-align: left;
}

.md th {
  background: var(--surface-sunk);
  font-weight: 500;
}

.md hr {
  height: 1px;
  border: none;
  background: var(--rule);
}

.md img {
  border-radius: var(--radius-sm);
}
</style>
