/**
 * localStorage 读写包装
 *
 * 全部读写都做异常兜底：隐私模式、容量写满（QuotaExceededError）、
 * 手工改坏的 JSON 都不能让应用白屏 —— 存储不可用时退化为「本次会话内有效」。
 */

export function readJSON(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw)
  } catch (e) {
    console.warn(`[cogagent] 读取本地存储 ${key} 失败，已回退到默认值`, e)
    return fallback
  }
}

export function writeJSON(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch (e) {
    console.warn(`[cogagent] 写入本地存储 ${key} 失败，数据仅在本次会话内保留`, e)
  }
}

/**
 * 防抖写盘
 *
 * 必要性：流式输出时每个 token 都会改动 store，若在 watch 里直接落盘，
 * 一次回答就可能触发上千次「序列化整个列表 + 写 localStorage」，
 * 主线程会被 JSON.stringify 卡死，界面直接掉帧。
 * 拖尾 400ms 足以覆盖连续写入，同时保证最后一次改动一定会落盘。
 *
 * @param {string} key
 * @param {() => any} getValue 延迟到真正写盘时才取值，避免提前序列化
 * @param {number} wait
 */
const pending = new Map()


export function debouncedWriteJSON(key, getValue, wait = 400) {
  const existing = pending.get(key)
  if (existing) clearTimeout(existing.timer)

  const entry = {}
  entry.getValue = getValue
  entry.timer = setTimeout(() => {
    pending.delete(key)
    writeJSON(key, entry.getValue())
  }, wait)

  pending.set(key, entry)
}

/**
 * 立即把所有等待中的写入刷盘
 * 挂在 beforeunload 上，否则用户刚发完消息就关页面时，
 * 最后 400ms 内的改动会随定时器一起被丢掉。
 */
export function flushPendingWrites() {
  for (const [key, entry] of pending) {
    clearTimeout(entry.timer)
    pending.delete(key)
    writeJSON(key, entry.getValue())
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('beforeunload', flushPendingWrites)
  // 移动端切到后台时不一定触发 beforeunload，补一个
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flushPendingWrites()
  })
}

export function removeKey(key) {
  try {
    localStorage.removeItem(key)
  } catch {
    /* 忽略 */
  }
}

/** 命名空间化的存储键，避免与同域下其它应用冲突 */
export const STORAGE_KEYS = {
  settings: 'cogagent.settings.v1',
  conversations: 'cogagent.conversations.v1',
  documents: 'cogagent.documents.v1',
  telemetry: 'cogagent.telemetry.v1',
}
