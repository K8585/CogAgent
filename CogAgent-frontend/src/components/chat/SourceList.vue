<script setup>
import { computed, ref } from 'vue'
import { formatScore, shortId } from '@/utils/format'

/**
 * 检索来源
 *
 * 呈现 ChatResponse.sources —— RAG 检索命中的文档切片。
 *
 * 注意：后端只在 handleRAG 真正执行时才填充这个字段（sources 为空时置 null），
 * 并且经过 Reranker 精排后最多保留 top-n（默认 3）条。
 * 因此「来源为空」有两种可能：没触发检索（意图不需要 RAG），或检索了但没命中。
 * 这两种情况都无法从响应里区分，界面上不做臆测。
 */
const props = defineProps({
  sources: { type: Array, default: () => [] },
})

const expanded = ref(new Set())

function toggle(id) {
  const next = new Set(expanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expanded.value = next
}

const sorted = computed(() => [...props.sources].sort((a, b) => (b.score || 0) - (a.score || 0)))

/** 相似度过低时用次要色，避免误导成高置信 */
function scoreTone(score) {
  if (score >= 0.75) return 'high'
  if (score >= 0.55) return 'mid'
  return 'low'
}
</script>

<template>
  <section v-if="sources.length" class="sources">
    <div class="sources__head">
      <span class="sources__label">检索来源</span>
      <span class="mono sources__count">{{ sources.length }} 条</span>
    </div>

    <ul class="sources__list">
      <li v-for="src in sorted" :key="src.documentId" class="sources__item">
        <button type="button" class="sources__row" @click="toggle(src.documentId)">
          <span class="mono sources__score" :class="`is-${scoreTone(src.score)}`">
            {{ formatScore(src.score) }}
          </span>
          <span class="mono sources__id">{{ shortId(src.documentId) }}</span>
          <span class="sources__preview">{{ src.content }}</span>
        </button>

        <!-- 相似度条：分数本身就是比例，用条比纯数字更快看出差距 -->
        <div class="sources__bar" aria-hidden="true">
          <span
            class="sources__bar-fill"
            :class="`is-${scoreTone(src.score)}`"
            :style="{ width: `${Math.max(0, Math.min(1, src.score || 0)) * 100}%` }"
          />
        </div>

        <pre v-if="expanded.has(src.documentId)" class="sources__full">{{ src.content }}</pre>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.sources {
  margin-bottom: 14px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--surface);
  overflow: hidden;
}

.sources__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 7px 11px;
  border-bottom: 1px solid var(--rule);
}

.sources__label {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--ink);
}

.sources__count {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.sources__item {
  padding: 0 0 8px;
  border-bottom: 1px solid var(--rule);
}

.sources__item:last-child {
  border-bottom: none;
  padding-bottom: 6px;
}

.sources__row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  width: 100%;
  padding: 8px 11px 5px;
  text-align: left;
}

.sources__row:hover {
  background: var(--paper);
}

.sources__score {
  flex: none;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.sources__score.is-high {
  color: var(--celadon);
}
.sources__score.is-mid {
  color: var(--ink-2);
}
.sources__score.is-low {
  color: var(--ink-3);
}

.sources__id {
  flex: none;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.sources__preview {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--ink-2);
  /* 折叠态只露一行，点击后展开全文 */
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sources__bar {
  height: 2px;
  margin: 0 11px;
  background: var(--surface-sunk);
  border-radius: 1px;
  overflow: hidden;
}

.sources__bar-fill {
  display: block;
  height: 100%;
  border-radius: 1px;
}

.sources__bar-fill.is-high {
  background: var(--celadon);
}
.sources__bar-fill.is-mid {
  background: var(--ink-3);
}
.sources__bar-fill.is-low {
  background: var(--ink-4);
}

.sources__full {
  margin: 8px 11px 0;
  padding: 8px 9px;
  border-radius: var(--radius-xs);
  background: var(--surface-sunk);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  line-height: 1.65;
  color: var(--ink-2);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow-y: auto;
}
</style>
