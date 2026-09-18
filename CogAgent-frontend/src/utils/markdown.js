import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * Markdown 渲染
 *
 * 模型输出属于不可信内容，必须经过 DOMPurify 消毒后再 v-html，
 * 否则一段包含 <img onerror=...> 的回复就能拿到页面上下文。
 *
 * 这里刻意不使用自定义 Renderer 覆盖 link 方法 —— marked 各版本间
 * Renderer 的签名变过（token 对象 vs 位置参数），直接覆盖容易在升级后静默失效。
 * 改用 DOMPurify 的 afterSanitizeAttributes 钩子给所有链接补上安全属性，
 * 与 marked 版本解耦。
 */

marked.setOptions({
  gfm: true, // 支持表格、删除线、任务列表
  breaks: true, // 单个换行即视为换行，更符合对话场景的直觉
})

DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    const href = node.getAttribute('href') || ''
    // 只放行 http/https/mailto，阻断 javascript: 与 data: 协议
    if (/^(https?:|mailto:)/i.test(href)) {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer')
    } else {
      node.removeAttribute('href')
    }
  }
})

/**
 * 把 Markdown 文本渲染为可安全注入的 HTML
 * @param {string} text 模型输出的原始文本
 * @returns {string} 消毒后的 HTML
 */
export function renderMarkdown(text) {
  if (!text) return ''
  try {
    const raw = marked.parse(String(text))
    // marked 在 async 模式下会返回 Promise，这里只接受同步结果，
    // 否则会把一个 Promise 对象当 HTML 洗一遍，得到空字符串。
    if (typeof raw !== 'string') throw new Error('marked 返回了非字符串结果')
    return DOMPurify.sanitize(raw, { USE_PROFILES: { html: true } })
  } catch {
    // 渲染失败时降级为纯文本，绝不让页面白屏
    return DOMPurify.sanitize(
      `<p>${String(text).replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' })[c])}</p>`
    )
  }
}
