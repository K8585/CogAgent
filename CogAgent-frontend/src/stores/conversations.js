import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { sendChat, streamChat } from '@/api/chat'
import { newConversationId, localId } from '@/utils/id'
import { STORAGE_KEYS, debouncedWriteJSON, readJSON } from './storage'
import { useSettingsStore } from './settings'
import { useTelemetryStore } from './telemetry'

/** 单个会话最多保留的消息条数，避免 localStorage 无限膨胀 */
const MAX_MESSAGES_PER_CONVERSATION = 120

/** 会话列表最多保留的会话数（按最后活动时间淘汰） */
const MAX_CONVERSATIONS = 40

/**
 * 会话
 *
 * 【为什么会话存在浏览器里】
 * 后端没有提供会话列表 / 历史查询接口（MemoryManager.getHistory 未对外暴露 HTTP），
 * 只有 Redis 里的短期记忆。因此会话列表、消息正文全部保存在 localStorage。
 *
 * 【会话 ID 的约定】
 * conversationId 由前端生成后传给后端，后端会原样沿用。这样浏览器里的会话
 * 与服务端 Redis 短期记忆、Milvus 长期记忆始终指向同一条会话，两边不会错位。
 * 反过来说：换一台电脑打开，看到的是空列表，而服务端仍然记得这段对话。
 */
export const useConversationsStore = defineStore('conversations', () => {
  const settings = useSettingsStore()
  const telemetry = useTelemetryStore()

  const list = ref(normalize(readJSON(STORAGE_KEYS.conversations, [])))
  const activeId = ref(list.value[0]?.id || null)

  /** 是否正在等待一次回答（同一时刻只允许一个请求在途） */
  const running = ref(false)

  /** 当前请求的 AbortController，用于「停止生成」 */
  let controller = null

  function normalize(raw) {
    if (!Array.isArray(raw)) return []
    return raw
      .filter((c) => c && typeof c.id === 'string')
      .map((c) => ({
        id: c.id,
        title: c.title || '未命名会话',
        createdAt: c.createdAt || Date.now(),
        updatedAt: c.updatedAt || c.createdAt || Date.now(),
        messages: Array.isArray(c.messages) ? c.messages : [],
      }))
  }

  const active = computed(() => list.value.find((c) => c.id === activeId.value) || null)

  /** 按最后活动时间倒序，用于左侧列表 */
  const sorted = computed(() => [...list.value].sort((a, b) => b.updatedAt - a.updatedAt))

  const canSend = computed(() => !running.value)

  function createConversation({ activate = true } = {}) {
    const now = Date.now()
    const conv = {
      id: newConversationId(),
      title: '新会话',
      createdAt: now,
      updatedAt: now,
      messages: [],
    }
    list.value = [conv, ...list.value].slice(0, MAX_CONVERSATIONS)
    if (activate) activeId.value = conv.id
    return list.value.find((c) => c.id === conv.id)
  }

  /** 取当前会话；没有就新建一个 */
  function ensureActive() {
    if (active.value) return active.value
    return createConversation()
  }

  function select(id) {
    activeId.value = id
  }

  function remove(id) {
    list.value = list.value.filter((c) => c.id !== id)
    if (activeId.value === id) {
      activeId.value = sorted.value[0]?.id || null
    }
  }

  function clearAll() {
    list.value = []
    activeId.value = null
  }

  /** 把同步接口返回的 ChatResponse 映射到消息对象上 */
  function applyResponse(msg, res) {
    msg.content = res?.reply ?? ''
    msg.thinkingSteps = res?.thinkingSteps ?? []
    msg.usedTools = res?.usedTools ?? []
    msg.sources = res?.sources ?? []
    msg.model = res?.model ?? null
    msg.tokenUsage = res?.tokenUsage ?? null
    msg.traceId = res?.traceId ?? null
  }

  /**
   * 发送一条消息
   * @param {string} text
   */
  async function send(text) {
    const trimmed = String(text || '').trim()
    if (!trimmed || running.value) return

    const conv = ensureActive()
    const now = Date.now()

    // —— 用户消息 ——
    conv.messages.push({
      id: localId('m'),
      role: 'user',
      content: trimmed,
      at: now,
    })

    // 首条用户消息充当会话标题
    const userCount = conv.messages.filter((m) => m.role === 'user').length
    if (userCount === 1) {
      conv.title = trimmed.length > 26 ? `${trimmed.slice(0, 26)}…` : trimmed
    }
    conv.updatedAt = now

    // —— 助手占位消息 ——
    conv.messages.push({
      id: localId('m'),
      role: 'assistant',
      content: '',
      at: Date.now(),
      status: 'running',
      thinkingSteps: [],
      usedTools: [],
      sources: [],
      model: null,
      tokenUsage: null,
      traceId: null,
      latencyMs: null,
      requestedMode: settings.mode,
      stream: settings.stream,
      error: null,
      errorCode: null,
    })

    // 取回响应式代理，后续所有写入都走它
    const assistant = conv.messages[conv.messages.length - 1]
    const conversationId = conv.id

    running.value = true
    const startedAt = performance.now()

    const payload = {
      conversationId,
      message: trimmed,
      mode: settings.mode,
      enableRag: settings.enableRag,
      tools: [...settings.tools],
      modelOptions: settings.modelOptions,
    }

    let failure = null

    try {
      if (settings.stream) {
        controller = new AbortController()
        await streamChat(payload, {
          signal: controller.signal,
          onDelta: (_delta, full) => {
            assistant.content = full
          },
          onDone: ({ aborted }) => {
            assistant.status = aborted ? 'aborted' : 'done'
            if (aborted && !assistant.content) {
              assistant.content = '（已停止生成）'
            }
          },
          onError: (err) => {
            failure = err
          },
        })
      } else {
        const res = await sendChat(payload)
        applyResponse(assistant, res)
        assistant.status = 'done'
      }
    } catch (err) {
      failure = err
    } finally {
      if (failure) {
        assistant.status = 'error'
        assistant.error = failure.message
        assistant.errorCode = failure.code ?? null
        assistant.traceId = failure.traceId ?? assistant.traceId
        if (!assistant.content) assistant.content = ''
      }
      assistant.latencyMs = Math.round(performance.now() - startedAt)
      running.value = false
      controller = null
      conv.updatedAt = Date.now()

      telemetry.record({
        kind: settings.stream ? 'chat-stream' : 'chat',
        status: assistant.status,
        latencyMs: assistant.latencyMs,
        requestedMode: settings.mode,
        stream: settings.stream,
        model: assistant.model,
        totalTokens: assistant.tokenUsage?.totalTokens ?? null,
        promptTokens: assistant.tokenUsage?.promptTokens ?? null,
        completionTokens: assistant.tokenUsage?.completionTokens ?? null,
        tools: assistant.usedTools || [],
        sourceCount: (assistant.sources || []).length,
        stepCount: (assistant.thinkingSteps || []).length,
        traceId: assistant.traceId,
        errorMessage: failure ? failure.message : null,
      })
    }
  }

  /** 停止生成（仅流式请求可中断） */
  function abort() {
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  /**
   * 落盘
   *
   * 必须防抖：流式输出时 list 每个 token 都会变，深度侦听随之触发，
   * 直接写盘就是「每个 token 序列化一次全部会话」，主线程会被拖垮。
   * 取值逻辑包在回调里，只在真正写盘时才对快照做裁剪。
   */
  watch(
    list,
    () => {
      debouncedWriteJSON(STORAGE_KEYS.conversations, () =>
        list.value.map((c) => ({
          ...c,
          messages: c.messages.slice(-MAX_MESSAGES_PER_CONVERSATION),
        }))
      )
    },
    { deep: true }
  )

  return {
    list,
    sorted,
    activeId,
    active,
    running,
    canSend,
    createConversation,
    ensureActive,
    select,
    remove,
    clearAll,
    send,
    abort,
  }
})
