import { API_BASE, ApiError, post } from './client'

/**
 * 对话接口
 * 对应后端 cn.edu.ai.trigger.http.ChatController
 */

/**
 * 组装请求体
 *
 * 两个刻意的「不发送」：
 *  1. mode 为空字符串（自动路由）时不带该字段 —— 后端
 *     AgentOrchestrator 只有在 request.getMode() == null 时才会走
 *     IntentRecognizer 路由；送一个空串反而会反序列化失败。
 *  2. tools 为空数组时不带该字段 —— 后端语义是「为空则使用全部可用工具」，
 *     送空数组等价于全选，没有「禁用全部工具」的表达方式。
 */
function buildBody({ conversationId, message, mode, enableRag, tools, modelOptions }) {
  const body = { message }
  if (conversationId) body.conversationId = conversationId
  if (mode) body.mode = mode
  body.enableRag = enableRag !== false
  if (Array.isArray(tools) && tools.length) body.tools = tools
  if (modelOptions && modelOptions.model) {
    // 后端只消费 modelOptions.model（用于 ModelRouter 强制指定模型），
    // temperature / maxTokens 目前不会被读取，因此不发送，避免造成「设置了但没生效」的误解。
    body.modelOptions = { model: modelOptions.model }
  }
  return body
}

/**
 * 同步对话
 * 返回完整结果：reply / thinkingSteps / usedTools / sources / tokenUsage / traceId
 * @returns {Promise<object>} ChatResponse
 */
export function sendChat(payload) {
  return post('/api/v1/chat', buildBody(payload))
}

/**
 * 解析单个 SSE 帧，取出 data 字段
 * @returns {string|null} 帧内数据；无数据行时返回 null
 */
function parseFrame(frame) {
  const dataLines = []
  for (const rawLine of frame.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (!line) continue
    if (line.startsWith('data:')) {
      // SSE 规范：'data:' 后若有一个前导空格应当去掉
      dataLines.push(line.slice(5).replace(/^ /, ''))
    } else if (
      line.startsWith(':') ||
      line.startsWith('event:') ||
      line.startsWith('id:') ||
      line.startsWith('retry:')
    ) {
      continue // 注释行（心跳）与其它字段，忽略
    } else if (dataLines.length) {
      // 裸行：Spring 的 SSE 编码器对含换行的文本可能直接写成多行而无 data: 前缀，
      // 视为上一数据行的续行，与浏览器 EventSource 的行为保持一致。
      dataLines.push(line)
    }
  }
  if (!dataLines.length) return null
  return dataLines.join('\n')
}

/**
 * 流式对话（SSE）
 *
 * 【必须知道的语义差异】
 * 后端 /api/v1/chat/stream 走的是 AgentOrchestrator.chatStream()，
 * 它直接调用 buildDirectPrompt + modelRouter.stream()，**不经过 Agent 编排**，
 * 因此不会产生 thinkingSteps / usedTools / sources —— 它只是一条带记忆上下文的
 * 纯文本流。界面上必须如实标注为「直通流式」，不能假装它有执行轨迹。
 * 需要看思考过程与工具调用时，请使用同步接口。
 *
 * 【为什么不用 EventSource】
 * SSE 规范只定义了 GET，而后端该接口是 POST。EventSource 无法发送
 * Content-Type: application/json 的请求体，因此这里用 fetch + ReadableStream
 * 手工解析 SSE。
 *
 * @param {object} payload 与 sendChat 相同的请求参数
 * @param {object} handlers
 * @param {(delta:string, full:string)=>void} handlers.onDelta 收到增量文本
 * @param {(result:{text:string, aborted?:boolean})=>void} handlers.onDone 流正常结束
 * @param {(err:ApiError)=>void} handlers.onError 出错
 * @param {AbortSignal} handlers.signal 用于「停止生成」
 */
export async function streamChat(payload, { onDelta, onDone, onError, signal } = {}) {
  const url = `${API_BASE}/api/v1/chat/stream`
  let response

  try {
    response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(buildBody(payload)),
      signal,
    })
  } catch (e) {
    if (e.name === 'AbortError') {
      // 用户主动停止：不算失败
      onDone?.({ text: '', aborted: true })
      return
    }
    const err = new ApiError(
      '连接不上后端服务。请确认 CogAgent 后端已在运行，并检查 vite.config.js 里 proxy 的指向（默认 http://localhost:8080）。',
      { code: 'NETWORK', raw: e }
    )
    onError?.(err)
    return
  }

  if (!response.ok) {
    // 失败时后端返回的是普通 JSON（Result 结构），而不是 SSE 流
    let message = `请求失败（HTTP ${response.status}）`
    let code = response.status
    let parsed = null

    try {
      parsed = await response.json()
      if (parsed && parsed.message) message = parsed.message
      if (parsed && parsed.code) code = parsed.code
    } catch {
      // 响应体不是 JSON —— 结合下面对状态码的判断，多半是代理转发失败
    }

    // 与 client.js 相同的判别：5xx 且非 Result 结构 = 请求没到后端。
    // Vite 代理在后端未启动时返回 500 + text/plain 空响应体。
    if (!parsed && response.status >= 500) {
      onError?.(
        new ApiError(
          '连接不上后端服务（请求未送达）。请确认 CogAgent 后端已在 8080 端口启动，' +
            '并检查 vite.config.js 中 proxy 的指向。',
          { code: 'NETWORK', httpStatus: response.status }
        )
      )
      return
    }

    onError?.(new ApiError(message, { code, httpStatus: response.status }))
    return
  }

  if (!response.body) {
    onError?.(new ApiError('当前浏览器不支持流式响应（ReadableStream 不可用）。', { code: 'NO_STREAM' }))
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let full = ''
  let finished = false

  try {
    while (!finished) {
      const { done, value } = await reader.read()
      if (done) break

      // 统一换行符，保证 '\n\n' 能匹配到 SSE 的事件分隔
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')

      let sep
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        const frame = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)

        const text = parseFrame(frame)
        if (text === null) continue
        if (text === '[DONE]') {
          // 后端在流末尾追加的结束标记
          finished = true
          break
        }
        full += text
        onDelta?.(text, full)
      }
    }

    // 流被关闭时可能残留一个没有以空行结尾的帧，补处理一次
    if (!finished && buffer.trim()) {
      const text = parseFrame(buffer)
      if (text && text !== '[DONE]') {
        full += text
        onDelta?.(text, full)
      }
    }
  } catch (e) {
    if (e.name === 'AbortError') {
      onDone?.({ text: full, aborted: true })
      return
    }
    onError?.(new ApiError(`流式响应中断：${e.message}`, { code: 'STREAM', raw: e }))
    return
  } finally {
    try {
      await reader.cancel()
    } catch {
      /* 流已自然结束，忽略 */
    }
  }

  onDone?.({ text: full })
}
