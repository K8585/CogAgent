import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { uploadDocument } from '@/api/document'
import { fileExt } from '@/utils/format'
import { localId } from '@/utils/id'
import { STORAGE_KEYS, debouncedWriteJSON, readJSON } from './storage'
import { useTelemetryStore } from './telemetry'

const MAX_RECORDS = 200

/**
 * 知识库文档记录
 *
 * 后端没有文档列表接口（DocumentController 只有 upload），
 * 因此这里保存的是「本浏览器上传过的文档」。
 *
 * 上传接口的响应只有 documentId / fileName / chunkCount / status / processingTimeMs，
 * 没有文件大小和时间戳 —— 列表里的 size 与 at 两列由前端在发起请求时补记，
 * 界面上的「大小」列据此标注来源。
 */
export const useDocumentsStore = defineStore('documents', () => {
  const telemetry = useTelemetryStore()

  const records = ref(normalize(readJSON(STORAGE_KEYS.documents, [])))

  /** 正在上传中的任务：{ taskId, fileName, size, percent, startedAt } */
  const pending = ref([])

  function normalize(raw) {
    if (!Array.isArray(raw)) return []
    return raw.filter((r) => r && r.id)
  }

  const sorted = computed(() =>
    [...records.value].sort((a, b) => (b.at || 0) - (a.at || 0))
  )

  const summary = computed(() => {
    const ok = records.value.filter((r) => r.status !== 'error')
    return {
      total: records.value.length,
      ok: ok.length,
      failed: records.value.length - ok.length,
      chunks: ok.reduce((sum, r) => sum + (r.chunkCount || 0), 0),
      totalBytes: ok.reduce((sum, r) => sum + (r.size || 0), 0),
      avgMs: ok.length
        ? Math.round(ok.reduce((sum, r) => sum + (r.processingTimeMs || 0), 0) / ok.length)
        : null,
    }
  })

  /**
   * 上传并处理一个文件
   * @param {File} file
   * @returns {Promise<object>} 上传记录
   */
  async function upload(file) {
    const task = {
      taskId: localId('u'),
      fileName: file.name,
      size: file.size,
      percent: 0,
      startedAt: Date.now(),
    }
    pending.value = [...pending.value, task]

    const record = {
      id: localId('d'),
      documentId: null,
      fileName: file.name,
      ext: fileExt(file.name),
      size: file.size,
      chunkCount: 0,
      status: 'processing',
      processingTimeMs: null,
      at: task.startedAt,
      error: null,
    }

    const startedAt = performance.now()

    try {
      const res = await uploadDocument(file, {
        onProgress: (percent) => {
          const t = pending.value.find((p) => p.taskId === task.taskId)
          if (t) t.percent = percent
        },
      })
      Object.assign(record, {
        documentId: res?.documentId ?? null,
        chunkCount: res?.chunkCount ?? 0,
        // 后端返回的 status 是权威值（如 SUCCESS / COMPLETED），原样展示
        status: res?.status || 'done',
        processingTimeMs: res?.processingTimeMs ?? Math.round(performance.now() - startedAt),
      })
    } catch (err) {
      record.status = 'error'
      record.error = err.message
      record.processingTimeMs = Math.round(performance.now() - startedAt)
    } finally {
      pending.value = pending.value.filter((p) => p.taskId !== task.taskId)
      records.value = [record, ...records.value].slice(0, MAX_RECORDS)

      telemetry.record({
        kind: 'upload',
        status: record.status === 'error' ? 'error' : 'done',
        latencyMs: record.processingTimeMs,
        errorMessage: record.error,
      })
    }

    return record
  }

  function remove(id) {
    records.value = records.value.filter((r) => r.id !== id)
  }

  function clearAll() {
    records.value = []
  }

  watch(records, () => debouncedWriteJSON(STORAGE_KEYS.documents, () => records.value), { deep: true })

  return { records, sorted, pending, summary, upload, remove, clearAll }
})
