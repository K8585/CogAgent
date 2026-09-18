import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { percentile } from '@/utils/format'
import { localId } from '@/utils/id'
import { STORAGE_KEYS, debouncedWriteJSON, readJSON } from './storage'

/** 保留的观测样本上限，超出后丢弃最旧的 */
const MAX_SAMPLES = 300

/**
 * 运行观测
 *
 * 【数据来源必须说清楚】
 * 后端没有提供 trace 查询接口（TraceService 只写服务端日志），
 * 因此本页所有指标均由前端在每次请求前后自行采集。
 * 它统计的是「这个浏览器发出的请求」，不是服务端的全量流量 —— 界面上必须如实标注。
 *
 * 另一个已知边界：请求体里的 mode 为空（自动路由）时，后端实际选中了哪个模式
 * 并不会出现在 ChatResponse 里（该 DTO 没有 mode 字段），所以这里只能记下
 * 「用户选择的模式」。模式分布图据此标注为「按所选模式」。
 */
export const useTelemetryStore = defineStore('telemetry', () => {
  const samples = ref(readJSON(STORAGE_KEYS.telemetry, []))

  /**
   * 记录一次请求
   * @param {object} s
   * @param {string} s.kind 'chat' | 'chat-stream' | 'upload'
   */
  function record(s) {
    samples.value = [
      ...samples.value,
      {
        id: localId('t'),
        at: Date.now(),
        kind: 'chat',
        status: 'done',
        latencyMs: null,
        requestedMode: '',
        stream: false,
        model: null,
        totalTokens: null,
        promptTokens: null,
        completionTokens: null,
        tools: [],
        sourceCount: 0,
        stepCount: 0,
        traceId: null,
        errorMessage: null,
        ...s,
      },
    ].slice(-MAX_SAMPLES)
  }

  function clear() {
    samples.value = []
  }

  /** 按时间倒序，最新的在最前 */
  const recent = computed(() => [...samples.value].reverse())

  const stats = computed(() => {
    const all = samples.value
    const chats = all.filter((s) => s.kind === 'chat' || s.kind === 'chat-stream')
    const ok = chats.filter((s) => s.status === 'done' || s.status === 'aborted')
    const failed = chats.filter((s) => s.status === 'error')

    const latencies = chats.map((s) => s.latencyMs).filter((v) => typeof v === 'number').sort((a, b) => a - b)

    const tokens = chats.reduce((sum, s) => sum + (s.totalTokens || 0), 0)

    // 工具调用排行
    const toolCounter = new Map()
    for (const s of chats) {
      for (const t of s.tools || []) {
        toolCounter.set(t, (toolCounter.get(t) || 0) + 1)
      }
    }
    const toolRanking = [...toolCounter.entries()]
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count)

    // 模式分布（按用户所选模式统计，见文件顶部说明）
    const modeCounter = new Map()
    for (const s of chats) {
      const key = s.requestedMode || ''
      modeCounter.set(key, (modeCounter.get(key) || 0) + 1)
    }
    const modeDistribution = [...modeCounter.entries()]
      .map(([mode, count]) => ({ mode, count }))
      .sort((a, b) => b.count - a.count)

    const uploads = all.filter((s) => s.kind === 'upload')
    const uploadOk = uploads.filter((s) => s.status === 'done')

    return {
      total: chats.length,
      ok: ok.length,
      failed: failed.length,
      successRate: chats.length ? ok.length / chats.length : null,
      p50: percentile(latencies, 50),
      p95: percentile(latencies, 95),
      slowest: latencies.length ? latencies[latencies.length - 1] : null,
      tokens,
      avgTokens: ok.length ? Math.round(tokens / Math.max(ok.length, 1)) : null,
      avgSteps: chats.length
        ? chats.reduce((sum, s) => sum + (s.stepCount || 0), 0) / chats.length
        : null,
      avgSources: chats.length
        ? chats.reduce((sum, s) => sum + (s.sourceCount || 0), 0) / chats.length
        : null,
      ragHitRate: chats.length
        ? chats.filter((s) => (s.sourceCount || 0) > 0).length / chats.length
        : null,
      toolRanking,
      modeDistribution,
      uploads: uploads.length,
      uploadOk: uploadOk.length,
      lastAt: all.length ? all[all.length - 1].at : null,
    }
  })

  watch(samples, () => debouncedWriteJSON(STORAGE_KEYS.telemetry, () => samples.value), { deep: true })

  return { samples, recent, stats, record, clear }
})
