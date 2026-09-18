import { onBeforeUnmount, onMounted, ref } from 'vue'
import { probeBackend, probeHealth } from '@/api/health'

/**
 * 自动轮询间隔
 *
 * 常规轮询打的是 /actuator/health：不碰业务逻辑、不写业务日志、开销可忽略，
 * 30 秒足够反映后端状态变化。真正有成本的是完整链路校验（会写一条 WARN），
 * 那个只在用户主动点击时执行。
 */
const POLL_INTERVAL = 30000

/**
 * 后端连通状态
 *
 * 全局单例：多个组件同时使用时共享同一份状态与同一个轮询定时器，
 * 避免每个用到它的组件各发一轮探测请求。
 */
const state = ref({
  /** unknown | checking | online | degraded | offline */
  status: 'unknown',
  /** 是否通过了「这不只是某个服务，而是 CogAgent」的验证 */
  verified: false,
  /** 服务可达但依赖组件异常 */
  degraded: false,
  /** 本次读数来自哪条探测路径：health（常规轮询）| chain（完整链路校验） */
  via: null,
  latencyMs: null,
  detail: '尚未探测',
  httpStatus: null,
  checkedAt: null,
})

let timer = null
let refCount = 0

/** 把探测结果写进共享状态，统一推导出 status */
function apply(result) {
  state.value = {
    ...result,
    status: result.online ? (result.degraded ? 'degraded' : 'online') : 'offline',
    checkedAt: Date.now(),
  }
}

/** 常规轮询：只打健康端点，静默 */
async function check() {
  state.value = { ...state.value, status: 'checking' }
  apply(await probeHealth())
}

/**
 * 完整链路校验：向业务接口发一次空 message，验证路由、参数校验、
 * 全局异常处理整条链路，并确认打到的确实是 CogAgent。
 *
 * 只由用户主动触发——后端会因此写一条 WARN 日志。
 */
async function verify() {
  state.value = { ...state.value, status: 'checking' }
  apply(await probeBackend())
}

/**
 * 在组件中使用连通状态
 * @param {object} options
 * @param {boolean} options.auto 是否自动轮询（默认 true）
 */
export function useConnection({ auto = true } = {}) {
  onMounted(() => {
    refCount += 1
    if (!auto) return
    if (refCount === 1) {
      check()
      timer = setInterval(check, POLL_INTERVAL)
    }
  })

  onBeforeUnmount(() => {
    refCount -= 1
    if (refCount <= 0) {
      refCount = 0
      if (timer) {
        clearInterval(timer)
        timer = null
      }
    }
  })

  return { connection: state, check, verify }
}
