import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ALL_TOOL_VALUES, findMode } from '@/constants/agent'
import { STORAGE_KEYS, readJSON, writeJSON } from './storage'

/**
 * 运行参数
 *
 * 对应后端 ChatRequest 的字段：mode / enableRag / tools / modelOptions。
 * 全部持久化到 localStorage —— 调参是连续动作，刷新页面后不该重置。
 */
export const useSettingsStore = defineStore('settings', () => {
  const saved = readJSON(STORAGE_KEYS.settings, {})

  /** Agent 模式；空字符串表示「自动路由」，即请求体不携带 mode 字段 */
  const mode = ref(typeof saved.mode === 'string' ? saved.mode : '')

  /** 是否启用 RAG 检索 */
  const enableRag = ref(saved.enableRag !== false)

  /**
   * 勾选的工具。后端语义是「为空则使用全部工具」，
   * 因此这里至少保留一项（见 toggleTool 的处理）。
   */
  const tools = ref(
    Array.isArray(saved.tools) && saved.tools.length
      ? saved.tools.filter((v) => ALL_TOOL_VALUES.includes(v))
      : [...ALL_TOOL_VALUES]
  )

  /** 是否使用 SSE 流式接口 */
  const stream = ref(saved.stream === true)

  /** 强制指定的模型名；留空则用后端的默认路由（主力/备用自动切换） */
  const model = ref(typeof saved.model === 'string' ? saved.model : '')

  /** 传给后端的 modelOptions；后端只消费 model 字段 */
  const modelOptions = computed(() => (model.value.trim() ? { model: model.value.trim() } : null))

  /** 当前模式的中文名，用于界面上的一行摘要 */
  const modeLabel = computed(() => findMode(mode.value).label)

  /** 所选模式是否真的会把工具交给 Agent —— Reflection 与 Direct 会忽略 tools */
  const toolsApply = computed(() => findMode(mode.value).consumesTools)

  function toggleTool(value) {
    const idx = tools.value.indexOf(value)
    if (idx === -1) {
      tools.value = [...tools.value, value]
    } else {
      // 后端没有「禁用全部工具」的表达方式：tools 为空即代表使用全部。
      // 所以最后一项不允许取消，否则界面上的「不选」会变成「全选」，语义反转。
      if (tools.value.length === 1) return false
      tools.value = tools.value.filter((v) => v !== value)
    }
    return true
  }

  function setMode(value) {
    mode.value = value
  }

  function reset() {
    mode.value = ''
    enableRag.value = true
    tools.value = [...ALL_TOOL_VALUES]
    stream.value = false
    model.value = ''
  }

  watch(
    [mode, enableRag, tools, stream, model],
    () => {
      writeJSON(STORAGE_KEYS.settings, {
        mode: mode.value,
        enableRag: enableRag.value,
        tools: tools.value,
        stream: stream.value,
        model: model.value,
      })
    },
    { deep: true }
  )

  return {
    mode,
    enableRag,
    tools,
    stream,
    model,
    modelOptions,
    modeLabel,
    toolsApply,
    toggleTool,
    setMode,
    reset,
  }
})
