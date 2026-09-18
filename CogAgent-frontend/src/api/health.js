import { API_BASE, post } from './client'

/**
 * 后端连通性探测
 *
 * 这里有两条探测路径，用途不同：
 *
 *   probeHealth() —— 常规轮询（每 30 秒）。打 actuator 健康端点，静默、开销可忽略。
 *   probeBackend() —— 用户主动触发的完整链路校验。打业务接口，能证明整条链路通畅，
 *                    代价是后端会写一条 WARN 日志。
 *
 * 【为什么常规轮询不再用空 message 探测】
 * 最初两条路径合二为一：每次轮询都发 POST /api/v1/chat { message: "" }，靠参数校验
 * 失败来证明后端在线。它对业务无副作用，但对日志不是静的——校验失败会走到
 * GlobalExceptionHandler.handleValidation()，那里是一行 log.warn：
 *
 *   log.warn("请求参数校验失败: message: 消息内容不能为空");
 *
 * 浏览器只要开着就每 30 秒刷一条，与用户停留在哪个页面无关，真正的参数校验失败
 * 会被这些噪声淹没。所以轮询改用 actuator，完整链路校验降级为手动触发。
 */

/** 后端校验失败时的文案，用来确认「打到的是 CogAgent 而不是别的服务」 */
const SIGNATURE = '消息内容不能为空'

/** 健康检查本身应当很快返回，超过这个时间就当作异常 */
const HEALTH_TIMEOUT = 8000

/**
 * 常规连通探测：GET /actuator/health
 *
 * 【为什么用 fetch 而不是 client.js 的 get()】
 * 两个原因：
 *
 *  1. /actuator/health 的响应是 actuator 自己的信封（{"status":"UP"}），
 *     不属于后端的 Result<T> 契约，不该走 client.js 的解包拦截器。
 *
 *  2. 更要紧的是——健康检查结果为 DOWN 时，Spring Boot 返回的是 **HTTP 503**。
 *     而 client.js 的拦截器把「5xx 且响应体不是 Result 结构」判定为
 *     「请求未送达」，那会把「后端在、但 Redis 挂了」误报成「连接不上后端服务」，
 *     正好把排查方向引反。这里直接读 HTTP 语义，按实际状态码判断。
 *
 * @returns {Promise<{online:boolean, verified:boolean, degraded:boolean, via:string,
 *                    latencyMs:number, detail:string, httpStatus:number|null}>}
 */
export async function probeHealth() {
  const startedAt = performance.now()
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), HEALTH_TIMEOUT)

  let response
  try {
    response = await fetch(`${API_BASE}/actuator/health`, {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    })
  } catch (e) {
    const latencyMs = Math.round(performance.now() - startedAt)
    const aborted = e.name === 'AbortError'
    return {
      online: false,
      verified: false,
      degraded: false,
      via: 'health',
      latencyMs,
      httpStatus: null,
      detail: aborted
        ? `健康检查超过 ${HEALTH_TIMEOUT / 1000} 秒未响应，后端可能仍在启动或已卡住。`
        : '连接不上后端服务。请确认 CogAgent 后端已在 8080 端口启动，并检查 vite.config.js 中 proxy 的指向。',
    }
  } finally {
    clearTimeout(timer)
  }

  const latencyMs = Math.round(performance.now() - startedAt)

  // 拿不到 JSON 不影响「服务可达」的判断，只是读不到具体状态
  let status = null
  try {
    status = (await response.json())?.status ?? null
  } catch {
    /* 响应体不是 JSON */
  }

  // 200 + UP：一切正常
  if (status === 'UP') {
    return {
      online: true,
      verified: true,
      degraded: false,
      via: 'health',
      latencyMs,
      httpStatus: response.status,
      detail: 'CogAgent 后端已就绪（/actuator/health 返回 UP）。',
    }
  }

  // 其余取值（DOWN / OUT_OF_SERVICE / UNKNOWN）：服务可达，但依赖组件有问题。
  // 这类情况下 Spring Boot 返回 503，所以不能按「连接失败」处理。
  if (status) {
    return {
      online: true,
      verified: false,
      degraded: true,
      via: 'health',
      latencyMs,
      httpStatus: response.status,
      detail:
        `后端可达，但健康检查为 ${status}。通常是 MySQL / Redis / Milvus 等依赖不可用——` +
        '此时对话和文档上传大概率会失败。',
    }
  }

  // 有 HTTP 响应但不是健康检查格式：端口被别的服务占了，或接口有变更
  return {
    online: true,
    verified: false,
    degraded: false,
    via: 'health',
    latencyMs,
    httpStatus: response.status,
    detail:
      `端口上有服务在响应（HTTP ${response.status}），但返回的不是 actuator 健康检查格式，` +
      '可能是别的服务占用了该端口。',
  }
}

/**
 * 完整链路校验：POST /api/v1/chat，body 为 { message: "" }
 *
 *   → Bean Validation 失败 → GlobalExceptionHandler → HTTP 400，message 含「消息内容不能为空」
 *
 * 比健康端点更有说服力，因为它一次性走通：
 *   路由注册 → Jackson 反序列化 → Bean Validation → 全局异常处理 → Result 包装
 *
 * 而且这条中文文案由本后端的 ChatRequest 与 GlobalExceptionHandler 共同决定，
 * 收到它就说明打到的确实是 CogAgent，而不是恰好占用了 8080 的其它 Spring 应用。
 *
 * 代价：后端会写一条 WARN 日志（见文件头的说明），因此只在用户主动点击时执行。
 *
 * @returns {Promise<{online:boolean, verified:boolean, degraded:boolean, via:string,
 *                    latencyMs:number, detail:string, httpStatus:number|null}>}
 */
export async function probeBackend() {
  const startedAt = performance.now()

  try {
    const data = await post('/api/v1/chat', { message: '' })
    // 走到这里说明空消息没被拒绝：服务可达，但行为与预期不符
    return {
      online: true,
      verified: false,
      degraded: false,
      via: 'chain',
      latencyMs: Math.round(performance.now() - startedAt),
      httpStatus: 200,
      detail: `服务可达，但空消息未被校验拦截（resp=${JSON.stringify(data)?.slice(0, 80)}）。请确认后端 Bean Validation 是否生效。`,
    }
  } catch (err) {
    const latencyMs = Math.round(performance.now() - startedAt)

    // 完全没拿到 HTTP 响应
    if (err.code === 'NETWORK' || err.code === 'TIMEOUT') {
      return {
        online: false,
        verified: false,
        degraded: false,
        via: 'chain',
        latencyMs,
        httpStatus: null,
        detail: err.message,
      }
    }

    // 拿到了预期的校验失败 —— 整条业务链路通畅
    if (err.httpStatus === 400 && String(err.message).includes(SIGNATURE)) {
      return {
        online: true,
        verified: true,
        degraded: false,
        via: 'chain',
        latencyMs,
        httpStatus: 400,
        detail: `CogAgent 后端已就绪（完整链路回执：${err.message}）`,
      }
    }

    // 有 HTTP 响应但不是预期结果：服务在，但可能不是这个后端、或接口有变更
    return {
      online: true,
      verified: false,
      degraded: false,
      via: 'chain',
      latencyMs,
      httpStatus: err.httpStatus ?? null,
      detail: `服务可达，但探测响应与预期不符：${err.message}`,
    }
  }
}
