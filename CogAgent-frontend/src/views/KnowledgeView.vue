<script setup>
import { computed } from 'vue'
import UploadDropzone from '@/components/knowledge/UploadDropzone.vue'
import EtlTrack from '@/components/knowledge/EtlTrack.vue'
import UploadRecords from '@/components/knowledge/UploadRecords.vue'
import { useDocumentsStore } from '@/stores/documents'
import { formatBytes, formatMs, formatNumber } from '@/utils/format'

const documents = useDocumentsStore()

/** 同一时刻只展示一个进行中的任务；后端本身也是同步串行处理 */
const activeTask = computed(() => documents.pending[0] || null)

const latestRecord = computed(() => documents.sorted[0] || null)

/**
 * ETL 面板展示什么
 * 有任务在跑就展示它；否则展示最近一次的结果（成功或失败）。
 */
const etlState = computed(() => {
  if (activeTask.value) {
    return {
      status: 'processing',
      fileName: activeTask.value.fileName,
      startedAt: activeTask.value.startedAt,
      record: null,
      error: null,
    }
  }
  const record = latestRecord.value
  if (!record) {
    return { status: 'idle', fileName: '', startedAt: null, record: null, error: null }
  }
  if (record.status === 'error') {
    return { status: 'error', fileName: record.fileName, startedAt: null, record, error: record.error }
  }
  // 后端返回的 status 若不是成功语义也别当成失败，这里只区分「有错误」与「无错误」
  return { status: 'done', fileName: record.fileName, startedAt: null, record, error: null }
})

const stats = computed(() => documents.summary)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>知识库</h1>
      <p>
        上传文档后，后端会同步走完 <strong>解析 → 切片 → 向量化 → 入库</strong> 全流程，
        切好的文本块进入 Milvus 的 documents collection，之后对话时的 RAG 检索就从这里召回。
        上传是可选的：不传文档也能正常对话，只是检索不到任何来源。
      </p>
    </header>

    <!-- ———————————————— 上传 ———————————————— -->
    <UploadDropzone @upload="documents.upload($event)" />

    <div v-if="activeTask" class="uploading">
      <span class="uploading__name mono">{{ activeTask.fileName }}</span>
      <span class="uploading__meta mono">
        {{ formatBytes(activeTask.size) }} · 已传输 {{ activeTask.percent }}%
      </span>
      <div class="uploading__bar">
        <span class="uploading__fill" :style="{ width: `${activeTask.percent}%` }" />
      </div>
    </div>

    <!-- ———————————————— ETL 管线 ———————————————— -->
    <EtlTrack
      :status="etlState.status"
      :file-name="etlState.fileName"
      :started-at="etlState.startedAt"
      :record="etlState.record"
      :error="etlState.error"
    />

    <!-- ———————————————— 统计 ———————————————— -->
    <section v-if="stats.total" class="stats">
      <div class="stat">
        <span class="stat__value mono">{{ formatNumber(stats.total) }}</span>
        <span class="stat__label">文档</span>
      </div>
      <div class="stat">
        <span class="stat__value mono">{{ formatNumber(stats.chunks) }}</span>
        <span class="stat__label">切片总数</span>
      </div>
      <div class="stat">
        <span class="stat__value mono">{{ formatBytes(stats.totalBytes) }}</span>
        <span class="stat__label">累计大小</span>
      </div>
      <div class="stat">
        <span class="stat__value mono">{{ formatMs(stats.avgMs) }}</span>
        <span class="stat__label">平均处理耗时</span>
      </div>
      <div class="stat" :class="{ 'stat--bad': stats.failed > 0 }">
        <span class="stat__value mono">{{ formatNumber(stats.failed) }}</span>
        <span class="stat__label">失败</span>
      </div>
    </section>

    <!-- ———————————————— 记录 ———————————————— -->
    <UploadRecords />
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
  margin-top: 20px;
}

.page__head {
  max-width: 78ch;
  margin-bottom: 26px;
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

.page__head strong {
  font-weight: 600;
  color: var(--ink);
}

/* —————————————— 上传进度 —————————————— */
.uploading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--surface);
}

.uploading__name {
  font-size: var(--fs-sm);
  color: var(--ink);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.uploading__meta {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  margin-left: auto;
}

.uploading__bar {
  width: 100%;
  height: 2px;
  background: var(--surface-sunk);
  border-radius: 1px;
  overflow: hidden;
}

.uploading__fill {
  display: block;
  height: 100%;
  background: var(--ochre);
  transition: width 0.2s var(--ease);
}

/* —————————————— 统计条 —————————————— */
.stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--surface);
  overflow: hidden;
}

.stat {
  flex: 1;
  min-width: 120px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 18px;
  border-right: 1px solid var(--rule);
}

.stat:last-child {
  border-right: none;
}

.stat__value {
  font-size: var(--fs-2xl);
  font-weight: 600;
  line-height: 1.25;
  color: var(--ink);
}

.stat--bad .stat__value {
  color: var(--rust);
}

.stat__label {
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

@media (max-width: 860px) {
  .page {
    padding: 22px 14px 44px;
  }
  .page__head h1 {
    font-size: var(--fs-2xl);
  }
  .stat {
    flex: 1 1 50%;
    min-width: 0;
  }
}
</style>
