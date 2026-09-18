import axios from 'axios'

/**
 * HTTP 客户端
 *
 * 后端统一返回结构 Result<T>：{ code, message, data, traceId }
 *   - code === 200 视为成功，data 为业务数据
 *   - 其余 code（含 400 / 500）都进失败分支
 * 业务层只应看到「解包后的 data」或一个 ApiError，不应再关心这层壳。
 */

/**
 * 接口基址
 * 开发期留空 → 使用相对路径 /api/v1/...，由 Vite devServer.proxy 转发到后端，
 * 浏览器视角始终同源，不触发跨域。
 * 生产期若前后端分域部署，由 .env.production 的 VITE_API_BASE_URL 指定。
 */
export const API_BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

/** 统一的接口错误类型，携带后端返回的 code / traceId 便于排查 */
export class ApiError extends Error {
  constructor(message, { code = null, httpStatus = null, traceId = null, raw = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.traceId = traceId
    this.raw = raw
  }
}

const http = axios.create({
  baseURL: API_BASE,
  // ReAct / Planner 会多轮调用大模型，单次请求可能持续很久，超时给足
  timeout: 180000,
})

/**
 * 解包 Result<T>
 * 遇到非 Result 结构（例如被网关改写的响应）时原样返回，
 * 避免在这里把有用的信息丢掉。
 */
function unwrap(response) {
  const body = response.data
  if (!body || typeof body !== 'object' || !('code' in body)) return body

  if (body.code !== 200) {
    throw new ApiError(body.message || `后端返回 code=${body.code}`, {
      code: body.code,
      httpStatus: response.status,
      traceId: body.traceId ?? null,
      raw: body,
    })
  }
  return body.data
}

http.interceptors.response.use(
  (response) => response,
  (error) => {
    // 主动取消（用户点了「停止」）不是故障，单独标记
    if (error.code === 'ERR_CANCELED' || error.name === 'CanceledError') {
      throw new ApiError('请求已取消', { code: 'CANCELED', raw: error })
    }

    const response = error.response

    // 压根没拿到响应：后端没起、代理目标写错、网络不通
    if (!response) {
      if (error.code === 'ECONNABORTED' || error.name === 'TimeoutError') {
        throw new ApiError('请求超时。Agent 多轮执行可能耗时较久，可查看后端日志确认进度。', {
          code: 'TIMEOUT',
          raw: error,
        })
      }
      throw new ApiError(
        '连接不上后端服务。请确认 CogAgent 后端已在运行，并检查 vite.config.js 里 proxy 的指向（默认 http://localhost:8080）。',
        { code: 'NETWORK', raw: error }
      )
    }

    const body = response.data
    const isResult = body && typeof body === 'object' && 'message' in body

    /**
     * 区分「后端返回了错误」与「请求根本没到后端」
     *
     * 开发期走 Vite 代理：后端没启动时，代理会返回 500，且 Content-Type 是
     * text/plain、响应体为空。而 CogAgent 的 GlobalExceptionHandler 在任何
     * 情况下都会返回 Result 结构的 application/json。
     *
     * 所以「5xx 且不是 Result 结构」基本可以断定代理没能把请求送达。
     * 这个判别很重要：否则最典型的故障（忘了启动后端）会被报成
     * 「服务返回 500」，把排查方向引到后端代码上去。
     */
    if (!isResult && response.status >= 500) {
      throw new ApiError(
        '连接不上后端服务（请求未送达）。请确认 CogAgent 后端已在 8080 端口启动，' +
          '并检查 vite.config.js 中 proxy 的指向。',
        { code: 'NETWORK', httpStatus: response.status, raw: body }
      )
    }

    throw new ApiError(
      isResult && body.message ? body.message : `请求失败（HTTP ${response.status}）`,
      {
        code: isResult ? body.code : response.status,
        httpStatus: response.status,
        traceId: isResult ? body.traceId ?? null : null,
        raw: body,
      }
    )
  }
)

/** POST 并解包 */
export async function post(path, payload, config) {
  return unwrap(await http.post(path, payload, config))
}

/** GET 并解包 */
export async function get(path, config) {
  return unwrap(await http.get(path, config))
}

export default http
