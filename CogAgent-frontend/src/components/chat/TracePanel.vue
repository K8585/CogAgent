<script setup>
import { computed, ref } from 'vue'
import { formatMs } from '@/utils/format'

/**
 * 执行轨迹
 *
 * 呈现后端 ChatResponse.thinkingSteps —— ReAct / Planner / Reflection 的推理过程。
 *
 * 默认折叠成一行，是因为多数时候读的是答案而不是过程；
 * 一旦展开，它就是这次请求真正的调用链，也是这个界面的性格所在。
 *
 * 【关于耗时】thinkingSteps 里没有每步的耗时（后端 ThinkingStep 只有
 * step / thought / action / stepResult 四个字段），所以这里显示的只能是
 * 「整次请求的总耗时」，且由前端计时。界面上必须如实标注，不能让读者
 * 误以为那是服务端的精确 Span 时长。
 */
const props = defineProps({
  steps: { type: Array, default: () => [] },
  latencyMs: { type: Number, default: null },
  usedTools: { type: Array, default: () => [] },
  /** 是否随内容到达而自动展开（流式/出错时不展开） */
  defaultOpen: { type: Boolean, default: false },
})

const open = ref(props.defaultOpen)

const stepCount = computed(() => props.steps.length)

const hasContent = computed(() => stepCount.value > 0 || props.usedTools.length > 0)
</script>

<template>
  <section v-if="hasContent" class="trace" :class="{ 'is-open': open }">
    <button
      type="button"
      class="trace__summary"
      :aria-expanded="open"
      @click="open = !open"
    >
      <span class="trace__caret" aria-hidden="true">{{ open ? '▾' : '▸' }}</span>
      <span class="trace__label">执行轨迹</span>
      <span class="trace__stats">
        <span v-if="stepCount" class="mono">{{ stepCount }} 步</span>
        <span v-if="usedTools.length" class="trace__sep">·</span>
        <span v-if="usedTools.length" class="mono">工具 {{ usedTools.join('、') }}</span>
        <span v-if="latencyMs !== null" class="trace__sep">·</span>
        <span v-if="latencyMs !== null" class="mono">{{ formatMs(latencyMs) }}</span>
      </span>
    </button>

    <ol v-if="open" class="trace__list">
      <li v-for="(step, i) in steps" :key="i" class="trace__step">
        <div class="trace__gutter">
          <span class="trace__index mono">{{ String(step.step ?? i + 1).padStart(2, '0') }}</span>
        </div>

        <div class="trace__body">
          <p v-if="step.thought" class="trace__thought">{{ step.thought }}</p>

          <div v-if="step.action" class="trace__field">
            <span class="trace__key">动作</span>
            <span class="tag tag--mono tag--accent">{{ step.action }}</span>
          </div>

          <div v-if="step.stepResult" class="trace__field">
            <span class="trace__key">观察</span>
            <pre class="trace__result">{{ step.stepResult }}</pre>
          </div>
        </div>
      </li>

      <li v-if="!steps.length" class="trace__empty">
        本次请求没有产生思考步骤（Direct 模式不经过编排）。
      </li>
    </ol>

    <p v-if="open && latencyMs !== null" class="trace__foot">
      总耗时由前端计时，覆盖从发出请求到收到完整响应的全过程；
      后端 <code class="mono">ThinkingStep</code> 未提供单步耗时。
    </p>
  </section>
</template>

<style scoped>
.trace {
  margin-bottom: 14px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--surface);
  overflow: hidden;
}

.trace.is-open {
  border-color: var(--rule-strong);
}

/* —— 折叠态的一行摘要 —— */
.trace__summary {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 11px;
  font-size: var(--fs-sm);
  color: var(--ink-2);
  text-align: left;
  transition: background-color 0.12s var(--ease);
}

.trace__summary:hover {
  background: var(--paper);
}

.trace__caret {
  flex: none;
  width: 9px;
  color: var(--ink-3);
  font-size: 9px;
}

.trace__label {
  flex: none;
  color: var(--ink);
  font-weight: 500;
}

.trace__stats {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace__sep {
  color: var(--ink-4);
}

/* —— 展开态的步骤列表：一条纵向轴线 —— */
.trace__list {
  border-top: 1px solid var(--rule);
  padding: 4px 0;
}

.trace__step {
  display: flex;
  gap: 12px;
  padding: 10px 11px 10px 14px;
  position: relative;
}

/* 轴线上每一节之间的连线 */
.trace__step + .trace__step::before {
  content: '';
  position: absolute;
  left: 23px;
  top: -8px;
  height: 12px;
  width: 1px;
  background: var(--rule);
}

.trace__gutter {
  flex: none;
}

.trace__index {
  display: inline-block;
  padding-top: 1px;
  color: var(--celadon);
  font-size: var(--fs-xs);
  font-weight: 600;
}

.trace__body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.trace__thought {
  font-size: var(--fs-md);
  line-height: 1.7;
  color: var(--ink);
  white-space: pre-wrap;
  word-break: break-word;
}

.trace__field {
  display: flex;
  gap: 9px;
  align-items: flex-start;
}

.trace__key {
  flex: none;
  width: 30px;
  padding-top: 2px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.trace__result {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 7px 9px;
  border-radius: var(--radius-xs);
  background: var(--surface-sunk);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  line-height: 1.62;
  color: var(--ink-2);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 190px;
  overflow-y: auto;
}

.trace__empty {
  padding: 10px 14px;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.trace__foot {
  padding: 8px 14px 10px;
  border-top: 1px solid var(--rule);
  font-size: var(--fs-xs);
  line-height: 1.65;
  color: var(--ink-3);
}

.trace__foot code {
  padding: 1px 4px;
  border-radius: var(--radius-xs);
  background: var(--surface-sunk);
}
</style>
