/**
 * 格式化工具
 * 界面里所有数值都应当走这里，保证同类数据在全站呈现一致。
 */

/** 毫秒 → 人类可读耗时。1000ms 以下保留整数毫秒，以上换算成秒 */
export function formatMs(ms) {
  if (ms === null || ms === undefined || Number.isNaN(ms)) return '—'
  if (ms < 1000) return `${Math.round(ms)}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`
  const m = Math.floor(ms / 60000)
  const s = Math.round((ms % 60000) / 1000)
  return `${m}m${String(s).padStart(2, '0')}s`
}

/** 千分位整数 */
export function formatNumber(n) {
  if (n === null || n === undefined || Number.isNaN(n)) return '—'
  return Number(n).toLocaleString('zh-CN')
}

/** 紧凑计数：1200 → 1.2k，用于空间紧张的指标位 */
export function formatCompact(n) {
  if (n === null || n === undefined) return '—'
  if (n < 1000) return String(n)
  if (n < 1000000) return `${(n / 1000).toFixed(n < 10000 ? 1 : 0)}k`
  return `${(n / 1000000).toFixed(1)}M`
}

/** 字节数 */
export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined || Number.isNaN(bytes)) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

/** 时分秒 */
function pad(n) {
  return String(n).padStart(2, '0')
}

export function formatClock(ts) {
  const d = new Date(ts)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function formatClockSec(ts) {
  const d = new Date(ts)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 相对日期：今天 / 昨天 / MM-DD / YYYY-MM-DD —— 用于会话列表分组 */
export function formatDay(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const dayMs = 86400000
  if (ts >= startOfToday) return '今天'
  if (ts >= startOfToday - dayMs) return '昨天'
  if (d.getFullYear() === now.getFullYear()) {
    return `${d.getMonth() + 1} 月 ${d.getDate()} 日`
  }
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 完整时间戳，用于表格中的精确时间列 */
export function formatDateTime(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes()
  )}:${pad(d.getSeconds())}`
}

/**
 * 截断长 ID 便于阅读：a3f29b1c7d4e... → a3f29b1c…7d4e
 * 中间省略保留首尾，方便肉眼比对两条记录是否同源。
 */
export function shortId(id, head = 8, tail = 4) {
  if (!id) return '—'
  const s = String(id)
  if (s.length <= head + tail + 1) return s
  return `${s.slice(0, head)}…${s.slice(-tail)}`
}

/** 相似度分数：0.8321 → 0.83 */
export function formatScore(score) {
  if (score === null || score === undefined || Number.isNaN(score)) return '—'
  return Number(score).toFixed(2)
}

/** 依据文件名推断扩展名（小写，不含点） */
export function fileExt(name) {
  const idx = String(name || '').lastIndexOf('.')
  return idx === -1 ? '' : String(name).slice(idx + 1).toLowerCase()
}

/**
 * 百分位：传入已排序（升序）的数值数组，返回 p 分位值。
 * 用最近秩法，样本量小时不会插值出数据中不存在的数字。
 */
export function percentile(sortedAsc, p) {
  if (!sortedAsc.length) return null
  const rank = Math.ceil((p / 100) * sortedAsc.length)
  return sortedAsc[Math.min(Math.max(rank - 1, 0), sortedAsc.length - 1)]
}
