/**
 * ID 生成
 *
 * 会话 ID 由前端生成后作为 conversationId 传给后端，后端会原样沿用
 * （AgentOrchestrator.generateConversationId 仅在为空时才新建）。
 * 这样浏览器里的会话与服务端 Redis 短期记忆、Milvus 长期记忆始终指向同一条会话。
 *
 * 格式刻意与后端保持一致：UUID 去掉连字符的 32 位十六进制串，
 * 便于在服务端日志里和 traceId 一起检索。
 */
export function newConversationId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID().replace(/-/g, '')
  }
  // 老浏览器兜底：crypto.getRandomValues 仍在，只是没有 randomUUID
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

/** 本地记录用的短 ID（消息、上传记录、观测样本） */
export function localId(prefix = 'r') {
  return `${prefix}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
}
