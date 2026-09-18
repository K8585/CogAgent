<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useConnection } from '@/composables/useConnection'
import { formatClockSec, formatMs } from '@/utils/format'

const { connection, verify } = useConnection()

const open = ref(false)
const root = ref(null)

const view = computed(() => {
  const c = connection.value
  switch (c.status) {
    case 'checking':
      return { tone: 'checking', text: '检测中', dot: 'var(--ink-4)' }
    case 'online':
      return c.verified
        ? { tone: 'ok', text: '已连接', dot: 'var(--celadon)' }
        : { tone: 'warn', text: '响应异常', dot: 'var(--ochre)' }
    // 服务能连上，但依赖组件（Redis / MySQL / Milvus）报了异常。
    // 与「未连接」分开表达：两者的排查方向完全不同。
    case 'degraded':
      return { tone: 'warn', text: '依赖异常', dot: 'var(--ochre)' }
    case 'offline':
      return { tone: 'bad', text: '未连接', dot: 'var(--rust)' }
    default:
      return { tone: 'idle', text: '待检测', dot: 'var(--ink-4)' }
  }
})

/**
 * 面板里的探测说明跟随实际使用的路径
 *
 * 常规轮询走健康端点、手动校验走业务接口，探测的东西不一样，不能写死一组文案。
 */
const probeMeta = computed(() =>
  connection.value.via === 'chain'
    ? {
        method: 'POST /api/v1/chat · message:""',
        expect: 'HTTP 400 · 参数校验失败',
        hint: '完整链路校验：走通路由注册、Jackson、Bean Validation 与全局异常处理。后端会因此写一条 WARN 日志，属预期行为。',
      }
    : {
        method: 'GET /actuator/health',
        expect: 'HTTP 200 · {"status":"UP"}',
        hint: '常规轮询走健康端点，不写业务日志。点下方按钮可做一次完整链路校验。',
      }
)

async function recheck() {
  await verify()
}

function onDocumentClick(e) {
  if (root.value && !root.value.contains(e.target)) open.value = false
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
</script>

<template>
  <div ref="root" class="conn">
    <button
      type="button"
      class="conn__trigger"
      :aria-expanded="open"
      aria-haspopup="dialog"
      @click="open = !open"
    >
      <span class="conn__dot" :style="{ background: view.dot }" />
      <span class="conn__text">{{ view.text }}</span>
      <span
        v-if="connection.status !== 'checking' && connection.latencyMs !== null"
        class="conn__ms mono"
      >
        {{ formatMs(connection.latencyMs) }}
      </span>
    </button>

    <div v-if="open" class="conn__pop" role="dialog" aria-label="后端连通状态">
      <div class="conn__pop-head">
        <span class="conn__dot" :style="{ background: view.dot }" />
        <strong>{{ view.text }}</strong>
      </div>

      <p class="conn__detail">{{ connection.detail }}</p>

      <dl class="conn__meta">
        <div>
          <dt>探测方式</dt>
          <dd class="mono">{{ probeMeta.method }}</dd>
        </div>
        <div>
          <dt>预期响应</dt>
          <dd class="mono">{{ probeMeta.expect }}</dd>
        </div>
        <div v-if="connection.httpStatus !== null">
          <dt>实际状态码</dt>
          <dd class="mono">{{ connection.httpStatus }}</dd>
        </div>
        <div v-if="connection.checkedAt">
          <dt>最近探测</dt>
          <dd class="mono">{{ formatClockSec(connection.checkedAt) }}</dd>
        </div>
      </dl>

      <p class="conn__hint">{{ probeMeta.hint }}</p>

      <div class="conn__actions">
        <button
          type="button"
          class="btn btn--sm"
          :disabled="connection.status === 'checking'"
          @click="recheck"
        >
          {{ connection.status === 'checking' ? '检测中…' : '完整校验' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.conn {
  position: relative;
}

.conn__trigger {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 26px;
  padding: 0 9px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--surface);
  font-size: var(--fs-sm);
  color: var(--ink-2);
  transition: border-color 0.12s var(--ease);
}

.conn__trigger:hover {
  border-color: var(--rule-strong);
}

.conn__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
}

.conn__text {
  color: var(--ink);
}

.conn__ms {
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.conn__pop {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 40;
  width: 330px;
  padding: 14px 15px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-pop);
  text-align: left;
}

.conn__pop-head {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  font-size: var(--fs-md);
}

.conn__detail {
  margin-bottom: 12px;
  font-size: var(--fs-sm);
  line-height: 1.65;
  color: var(--ink-2);
  word-break: break-word;
}

.conn__meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 10px 0;
  border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
}

.conn__meta > div {
  display: flex;
  gap: 10px;
  font-size: var(--fs-sm);
}

.conn__meta dt {
  flex: none;
  width: 76px;
  color: var(--ink-3);
}

.conn__meta dd {
  margin: 0;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  word-break: break-all;
}

.conn__hint {
  margin-top: 10px;
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--ink-3);
}

.conn__actions {
  margin-top: 11px;
}
</style>
