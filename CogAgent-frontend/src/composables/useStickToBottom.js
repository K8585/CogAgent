import { nextTick, ref } from 'vue'

/** 判定「已经贴在底部」的容差，避免因像素取整导致状态来回抖动 */
const THRESHOLD = 48

/**
 * 让滚动容器自动跟随最新内容
 *
 * 关键点：只有当用户本来就停在底部时才自动滚动。
 * 否则用户往上翻看历史时，每条新到达的流式片段都会把他拽回底部。
 *
 * @param {import('vue').Ref<HTMLElement|null>} elRef 滚动容器
 */
export function useStickToBottom(elRef) {
  /** 用户当前是否停留在底部 */
  const stuck = ref(true)

  function onScroll() {
    const el = elRef.value
    if (!el) return
    stuck.value = el.scrollHeight - el.scrollTop - el.clientHeight < THRESHOLD
  }

  function toBottom(behavior = 'auto') {
    const el = elRef.value
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior })
  }

  /** 内容变化后调用：只有本来就贴底才跟随 */
  async function keep() {
    if (!stuck.value) return
    await nextTick()
    toBottom('auto')
  }

  /** 强制回到底部（发送消息、切换会话时用） */
  async function jump() {
    stuck.value = true
    await nextTick()
    toBottom('auto')
  }

  return { stuck, onScroll, keep, jump }
}
