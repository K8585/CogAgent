<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { formatMs } from '@/utils/format'

/**
 * ETL 处理管线
 *
 * 【为什么这里没有分段进度条】
 * 后端的 /api/v1/documents/upload 是同步接口：一次性完成
 * 解析 → 切片 → 向量化 → 入库，然后返回最终结果，中途不推送任何进度。
 * 所以四个阶段在这里是「结果的分解说明」，不是「逐段推进的进度条」——
 * 处理中只显示真实计时，完成后四个阶段一起落定。
 * 给未知的中间状态编造进度，是这个页面最容易犯的错。
 */
const props = defineProps({
  /** idle | processing | done | error */
  status: { type: String, default: 'idle' },
  fileName: { type: String, default: '' },
  startedAt: { type: Number, default: null },
  record: { type: Object, default: null },
  error: { type: String, default: null },
})

const STAGES = [
  { name: '解析提取', impl: 'Apache Tika', note: '从 PDF / DOCX / HTML 等格式中抽取纯文本' },
  { name: '文本切片', impl: 'DocumentChunker', note: '按段落边界切分，带重叠保持上下文连续' },
  { name: '向量化', impl: 'text-embedding-v3', note: '为每个切片生成 1024 维向量' },
  { name: '写入向量库', impl: 'Milvus', note: '存入 documents collection，供 RAG 检索' },
]

/** 处理中的实时计时 */
const now = ref(Date.now())
let timer = null

function startTimer() {
  stopTimer()
  now.value = Date.now()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 100)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(
  () => props.status,
  (s) => {
    if (s === 'processing') startTimer()
    else stopTimer()
  },
  { immediate: true }
)

onBeforeUnmount(stopTimer)

/** 处理中显示实时秒表；完成后显示后端回传的真实耗时 */
const elapsedMs = computed(() => {
  if (props.status === 'processing' && props.startedAt) return now.value - props.startedAt
  return props.record?.processingTimeMs ?? null
})

const stageState = computed(() => {
  const map = { idle: 'idle', processing: 'active', done: 'done', error: 'error' }
  return map[props.status] || 'idle'
})
</script>

<template>
  <section class="etl" :class="`etl--${status}`">
    <div class="etl__head">
      <span class="etl__title">ETL 处理管线</span>
      <span v-if="fileName" class="etl__file mono">{{ fileName }}</span>

      <span class="etl__status">
        <template v-if="status === 'processing'">
          <span class="etl__spinner" aria-hidden="true" />
          处理中 {{ elapsedMs !== null ? formatMs(elapsedMs) : '' }}
        </template>
        <template v-else-if="status === 'done'">
          已完成 · {{ formatMs(record?.processingTimeMs) }}
        </template>
        <template v-else-if="status === 'error'">处理失败</template>
        <template v-else>等待上传</template>
      </span>
    </div>

    <ol class="etl__stages">
      <li v-for="stage in STAGES" :key="stage.name" class="stage" :class="`stage--${stageState}`">
        <div class="stage__rail">
          <span class="stage__node" aria-hidden="true" />
          <span class="stage__line" aria-hidden="true" />
        </div>
        <div class="stage__body">
          <h3 class="stage__name">{{ stage.name }}</h3>
          <p class="stage__impl mono">{{ stage.impl }}</p>
          <p class="stage__note">{{ stage.note }}</p>
        </div>
      </li>
    </ol>

    <div v-if="status === 'done' && record" class="etl__result">
      <div class="result">
        <span class="result__value mono">{{ record.chunkCount }}</span>
        <span class="result__label">个切片</span>
      </div>
      <div class="result">
        <span class="result__value mono">{{ formatMs(record.processingTimeMs) }}</span>
        <span class="result__label">处理耗时</span>
      </div>
      <div class="result result--wide">
        <span class="result__value mono result__value--sm">{{ record.documentId || '—' }}</span>
        <span class="result__label">文档 ID</span>
      </div>
    </div>

    <div v-else-if="status === 'error'" class="etl__error">
      <div class="notice notice--error">
        <div>
          <strong>{{ error || '文档处理失败' }}</strong>
          <p class="etl__error-hint">
            常见原因：格式不受 Tika 支持、文件已加密需要密码、或后端未连上
            Milvus / Embedding 服务。上传记录里保留了本次的失败信息。
          </p>
        </div>
      </div>
    </div>

    <p class="etl__foot">
      后端该接口是同步的，一次调用走完全部四个阶段才返回，中途不推送进度，
      因此这里不提供分段进度条。
    </p>
  </section>
</template>

<style scoped>
.etl {
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--surface);
  overflow: hidden;
}

.etl__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--rule);
}

.etl__title {
  font-size: var(--fs-lg);
  font-weight: 600;
}

.etl__file {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-sm);
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.etl__status {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

.etl--done .etl__status {
  color: var(--celadon);
}

.etl--error .etl__status {
  color: var(--rust);
}

.etl__spinner {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ochre);
  animation: spin-pulse 1.1s var(--ease) infinite;
}

@keyframes spin-pulse {
  0%,
  100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}

/* —————————————— 四阶段 —————————————— */
.etl__stages {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 16px 18px 14px;
}

.stage {
  padding-right: 16px;
}

.stage:last-child {
  padding-right: 0;
}

.stage__rail {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 10px;
  margin-bottom: 11px;
}

.stage__node {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1px solid var(--ink-4);
  background: var(--surface);
}

.stage__line {
  flex: 1;
  height: 1px;
  background: var(--rule);
}

.stage--done .stage__node {
  border-color: var(--celadon);
  background: var(--celadon);
}
.stage--done .stage__line {
  background: var(--rule-strong);
}

.stage--active .stage__node {
  border-color: var(--ochre);
  background: var(--ochre);
  animation: spin-pulse 1.1s var(--ease) infinite;
}

.stage--error .stage__node {
  border-color: var(--rust);
  background: var(--rust);
}

.stage__name {
  font-size: var(--fs-md);
  margin-bottom: 3px;
  color: var(--ink-2);
}

.stage--done .stage__name,
.stage--active .stage__name {
  color: var(--ink);
}

.stage__impl {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  margin-bottom: 3px;
}

.stage__note {
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--ink-3);
}

/* —————————————— 结果 —————————————— */
.etl__result {
  display: flex;
  flex-wrap: wrap;
  gap: 30px;
  padding: 14px 18px;
  border-top: 1px solid var(--rule);
  background: var(--surface-sunk);
}

.result {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.result--wide {
  min-width: 0;
  flex: 1;
}

.result__value {
  font-size: var(--fs-2xl);
  font-weight: 600;
  color: var(--ink);
  line-height: 1.2;
}

.result__value--sm {
  font-size: var(--fs-sm);
  font-weight: 400;
  color: var(--ink-2);
  word-break: break-all;
}

.result__label {
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.etl__error {
  padding: 14px 18px;
  border-top: 1px solid var(--rule);
}

.etl__error-hint {
  margin-top: 5px;
  line-height: 1.65;
  opacity: 0.85;
}

.etl__foot {
  padding: 10px 18px 11px;
  border-top: 1px solid var(--rule);
  font-size: var(--fs-xs);
  line-height: 1.65;
  color: var(--ink-3);
}

@media (max-width: 860px) {
  .etl__stages {
    grid-template-columns: minmax(0, 1fr);
  }
  .stage {
    padding: 0 0 14px 14px;
    border-left: 1px solid var(--rule);
    margin-left: 3px;
  }
  .stage:last-child {
    border-left-color: transparent;
    padding-bottom: 0;
  }
  .stage__line {
    display: none;
  }
  .stage__rail {
    margin-bottom: 5px;
  }
}
</style>
