<script setup>
import { computed, ref } from 'vue'
import { useSettingsStore } from '@/stores/settings'
import { INTENT_LABELS, MODE_OPTIONS, TOOLS, findMode } from '@/constants/agent'

/**
 * 运行参数
 *
 * 这一栏直接对应后端 ChatRequest 的四个字段，改什么就发什么，没有中间层。
 * 每项下面都写清了它在后端的真实行为，包括「什么时候不生效」——
 * 参数面板最容易骗人的地方就是让用户以为自己改了什么。
 */
const settings = useSettingsStore()

const currentMode = computed(() => findMode(settings.mode))

/** 工具被取消到最后一项时的提示 */
const toolLockedHint = ref(false)
let hintTimer = null

function onToggleTool(value) {
  const changed = settings.toggleTool(value)
  if (!changed) {
    // 后端没有「禁用全部工具」的表达方式，最后一项不能取消
    toolLockedHint.value = true
    clearTimeout(hintTimer)
    hintTimer = setTimeout(() => (toolLockedHint.value = false), 3200)
  }
}
</script>

<template>
  <aside class="aside">
    <div class="aside__head">
      <h2 class="aside__title">运行参数</h2>
      <button type="button" class="btn btn--sm btn--quiet" @click="settings.reset()">重置</button>
    </div>

    <div class="aside__body">
      <!-- —————— Agent 模式 —————— -->
      <section class="group">
        <div class="field">
          <label class="field__label" for="mode-select">Agent 模式</label>
          <select id="mode-select" v-model="settings.mode" class="select">
            <option v-for="opt in MODE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}{{ opt.cn ? ` · ${opt.cn}` : '' }}
            </option>
          </select>
        </div>

        <p class="field__hint">{{ currentMode.scene }}</p>

        <p v-if="!settings.mode" class="field__hint">
          请求体不携带 <code class="mono">mode</code> 字段，由后端的规则层与 LLM 层共同判定意图，
          再映射到 ReAct / Planner / Reflection / Direct。
        </p>
        <p v-else class="field__hint">
          请求体显式指定 <code class="mono">mode: "{{ settings.mode }}"</code>，跳过意图识别。
        </p>

        <div v-if="currentMode.intents.length" class="intents">
          <span class="intents__label">典型意图</span>
          <span v-for="intent in currentMode.intents" :key="intent" class="tag">
            {{ INTENT_LABELS[intent] || intent }}
          </span>
        </div>
      </section>

      <hr class="rule" />

      <!-- —————— 检索增强 —————— -->
      <section class="group">
        <label class="switch">
          <input v-model="settings.enableRag" type="checkbox" />
          <span class="switch__track" />
          <span class="switch__label">启用 RAG 检索</span>
        </label>
        <p class="field__hint">
          对应 <code class="mono">enableRag</code>。关闭后一定不检索；
          开启也要意图本身需要知识（如知识问答）才会真正触发向量召回。
          无论开关如何，命中结果都会出现在回答下方的「检索来源」里。
        </p>
      </section>

      <hr class="rule" />

      <!-- —————— 响应方式 —————— -->
      <section class="group">
        <label class="switch">
          <input v-model="settings.stream" type="checkbox" />
          <span class="switch__track" />
          <span class="switch__label">流式输出</span>
        </label>

        <div v-if="settings.stream" class="notice notice--warn">
          <div>
            流式接口走的是<strong>直通模型</strong>路径，不经过 Agent 编排，
            因此<strong>不会产生思考步骤、工具调用和检索来源</strong>，也不会应用上面的
            Agent 模式。需要看执行轨迹时请关掉它。
          </div>
        </div>
        <p v-else class="field__hint">
          调用 <code class="mono">POST /api/v1/chat</code>，一次返回完整结果，
          包含思考步骤、工具记录、检索来源与 token 用量。
        </p>
      </section>

      <hr class="rule" />

      <!-- —————— 工具 —————— -->
      <section class="group">
        <div class="group__head">
          <span class="field__label">可用工具</span>
          <span class="mono group__count">{{ settings.tools.length }}/{{ TOOLS.length }}</span>
        </div>

        <div class="tools">
          <button
            v-for="tool in TOOLS"
            :key="tool.value"
            type="button"
            class="tool"
            :class="{ 'is-on': settings.tools.includes(tool.value) }"
            :aria-pressed="settings.tools.includes(tool.value)"
            @click="onToggleTool(tool.value)"
          >
            <span class="tool__name">{{ tool.label }}</span>
            <span class="mono tool__key">{{ tool.value }}</span>
          </button>
        </div>

        <p v-if="toolLockedHint" class="field__hint field__hint--warn">
          后端把 <code class="mono">tools</code> 为空视为「使用全部工具」，
          没有「禁用全部」的表达方式，因此至少要保留一项。
        </p>

        <p v-if="!settings.toolsApply" class="field__hint">
          当前模式为 {{ currentMode.label }}，后端会忽略
          <code class="mono">tools</code> 字段，该选择暂不生效。
        </p>
        <p v-else class="field__hint">
          对应 <code class="mono">tools</code>，仅在 ReAct 与 Planner 模式下生效。
        </p>
      </section>

      <hr class="rule" />

      <!-- —————— 模型 —————— -->
      <section class="group">
        <div class="field">
          <label class="field__label" for="model-input">指定模型</label>
          <input
            id="model-input"
            v-model="settings.model"
            class="input mono"
            type="text"
            placeholder="留空使用后端默认路由"
          />
        </div>
        <p class="field__hint">
          对应 <code class="mono">modelOptions.model</code>。留空时由
          <code class="mono">ModelRouter</code> 在主力与备用模型间自动切换，
          并在连续失败时触发熔断降级；填了则强制走该模型，绕过熔断策略。
        </p>

        <p class="field__hint field__hint--muted">
          后端目前只读取 <code class="mono">modelOptions.model</code>，
          <code class="mono">temperature</code> 与 <code class="mono">maxTokens</code>
          虽在 DTO 中定义但未被消费，所以这里没有放对应的滑块。
        </p>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.aside {
  display: flex;
  flex-direction: column;
  width: var(--aside-w);
  flex: none;
  border-left: 1px solid var(--rule);
  background: var(--surface);
}

.aside__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 8px 10px 16px;
  border-bottom: 1px solid var(--rule);
}

.aside__title {
  font-size: var(--fs-md);
  font-weight: 600;
}

.aside__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: 22px;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 15px 16px;
}

.group__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.group__count {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.field__hint code,
.panel-foot code {
  padding: 1px 4px;
  border-radius: var(--radius-xs);
  background: var(--surface-sunk);
  font-size: 0.92em;
}

.field__hint--warn {
  color: var(--ochre);
}

.field__hint--muted {
  color: var(--ink-4);
}

/* —— 意图标签 —— */
.intents {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
}

.intents__label {
  margin-right: 2px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

/* —— 工具开关 —— */
.tools {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.tool {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 9px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  text-align: left;
  transition: border-color 0.12s var(--ease), background-color 0.12s var(--ease);
}

.tool:hover {
  border-color: var(--ink-4);
}

.tool.is-on {
  border-color: var(--celadon);
  background: var(--celadon-mist);
}

.tool__name {
  font-size: var(--fs-md);
  color: var(--ink-2);
}

.tool.is-on .tool__name {
  color: var(--celadon-deep);
  font-weight: 500;
}

.tool__key {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.tool.is-on .tool__key {
  color: var(--celadon);
}

/* —— 分隔线：略微缩进，避免与面板边界粘连 —— */
.rule {
  margin: 0 16px;
}

.notice {
  margin-top: 2px;
}
</style>
