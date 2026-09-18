import { ApiError, post } from './client'

/**
 * 文档接口
 * 对应后端 cn.edu.ai.trigger.http.DocumentController
 */

/** 后端 spring.servlet.multipart 的限制，见 application.yml */
export const MAX_FILE_SIZE = 50 * 1024 * 1024 // 单文件 50MB
export const MAX_REQUEST_SIZE = 100 * 1024 * 1024 // 单次请求 100MB

/** 后端 ETLPipeline 经 Apache Tika 解析，支持的主要格式 */
export const ACCEPTED_EXTENSIONS = ['pdf', 'docx', 'doc', 'txt', 'md', 'markdown', 'html', 'htm']

/**
 * 上传文档并触发 ETL
 * 后端会同步完成：解析 → 切片 → 向量化 → 入库，然后一次性返回结果。
 * 接口不返回阶段进度，因此调用方只能用真实耗时表达「正在处理」，
 * 不要伪造阶段进度条。
 *
 * @param {File} file
 * @param {object} options
 * @param {(percent:number)=>void} options.onProgress 上传进度（0-100，仅覆盖浏览器→后端的传输阶段）
 * @param {AbortSignal} options.signal
 * @returns {Promise<{documentId:string,fileName:string,chunkCount:number,status:string,processingTimeMs:number}>}
 */
export function uploadDocument(file, { onProgress, signal } = {}) {
  const form = new FormData()
  form.append('file', file)

  return post('/api/v1/documents/upload', form, {
    // 大文档的解析与向量化耗时较长，单独放宽超时
    timeout: 300000,
    signal,
    // 刻意不设 Content-Type：交表单给浏览器时，它会自动补上带 boundary 的
    // multipart/form-data。手工写死会丢掉分隔符，后端直接解析失败。
    onUploadProgress: (e) => {
      if (e.total) onProgress?.(Math.round((e.loaded / e.total) * 100))
    },
  })
}

/** 上传前的本地预校验，避免把注定失败的请求发出去 */
export function validateFile(file) {
  if (!file) return '未选择文件'
  if (file.size === 0) return '文件内容为空'
  if (file.size > MAX_FILE_SIZE) {
    return `文件 ${(file.size / 1024 / 1024).toFixed(1)}MB，超出后端 50MB 的单文件上限`
  }
  const ext = String(file.name).split('.').pop()?.toLowerCase() || ''
  if (!ACCEPTED_EXTENSIONS.includes(ext)) {
    return `暂不支持 .${ext} 格式，可上传 ${ACCEPTED_EXTENSIONS.slice(0, 6).join(' / ')} 等格式`
  }
  return null
}

export { ApiError }
