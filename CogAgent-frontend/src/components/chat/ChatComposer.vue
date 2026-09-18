<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useSettingsStore } from '@/stores/settings'
import { ALL_TOOL_VALUES, findMode } from '@/constants/agent'
import { formatMs } from '@/utils/format'

const props = defineProps({
  running: { type: Boolean, default: false },
  /** 当前会话累计的请求耗时中位数等，这里只用最后一条的信息做提示 */
  lastLatencyMs: { type: Number, default: null },
})

const emit = defineEmits(['send', 'stop'])

const settings = useSettingsStore()

const text = ref('')
const box = ref(null)
const composing = ref(false)

const canSend = computed(() => !props.running && text.value.trim().length > 0)

/** 输入框下方的配置摘要，让「这次请求会怎么走」在按下回车前就可见 */
const summary = computed(() => {
  const parts = [findMode(settings.mode).label]
  parts.push(settings.enableRag ? 'RAG 开' : 'RAG 关')
  if (findMode(settings.mode).consumesTools) {
    parts.push(`工具 ${settings.tools.length}/${ALL_TOOL_VALUES.length}`)
  } else {
    parts.push('工具不适用')
  }
  parts.push(settings.stream ? '直通流式' : '同步')
  if (settings.model) parts.push(settings.model)
  return parts
})

/** 随内容高度自适应，上限 200px 后转为内部滚动 */
async function autoResize() {
  await nextTick()
  const el = box.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 200)}px`
}

watch(text, autoResize)

function submit() {
  if (!canSend.value) return
  emit('send', text.value)
  text.value = ''
  autoResize()
}

function onKeydown(e) {
  if (e.key !== 'Enter') return

  /**
   * 中文输入法的关键处理：
   * 候选框未上屏时按 Enter 是「选词」，不是「发送」。
   * 不看 isComposing 的话，打「熔断器」三个字就会中途发出去三条。
   * keyCode 229 是老浏览器在 IME 组合期间的兼容写法。
   */
  if (composing.value || e.isComposing || e.keyCode === 229) return

  if (e.shiftKey) return // Shift+Enter 换行

  e.preventDefault()
  submit()
}

function onPaste() {
  // 粘贴后高度会变，等一帧再量
  autoResize()
}

defineExpose({ focus: () => box.value?.focus() })
</script>

<template>
  <div class="composer">
    <div class="composer__box" :class="{ 'is-running': running }">
      <textarea
        ref="box"
        v-model="text"
        class="composer__input"
        rows="1"
        placeholder="输入问题，Enter 发送，Shift + Enter 换行"
        :disabled="running"
        @keydown="onKeydown"
        @compositionstart="composing = true"
        @compositionend="composing = false"
        @paste="onPaste"
      />

      <div class="composer__actions">
        <button
          v-if="running"
          type="button"
          class="btn btn--sm"
          @click="emit('stop')"
        >
          停止
        </button>
        <button
          type="button"
          class="btn btn--primary btn--sm"
          :disabled="!canSend"
          @click="submit"
        >
          发送
        </button>
      </div>
    </div>

    <div class="composer__foot">
      <span class="composer__config">
        <span v-for="(part, i) in summary" :key="part" class="composer__config-item">
          <span v-if="i > 0" class="composer__dot">·</span>{{ part }}
        </span>
      </span>

      <span v-if="lastLatencyMs !== null" class="composer__latency mono">
        上次 {{ formatMs(lastLatencyMs) }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.composer {
  flex: none;
  padding: 12px 26px 14px;
  border-top: 1px solid var(--rule);
  background: var(--surface);
}

.composer__box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--radius);
  background: var(--surface);
  transition: border-color 0.12s var(--ease), box-shadow 0.12s var(--ease);
}

.composer__box:focus-within {
  border-color: var(--celadon);
  box-shadow: 0 0 0 3px var(--celadon-mist);
}

.composer__input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  resize: none;
  background: none;
  font-size: var(--fs-lg);
  line-height: 1.65;
  max-height: 200px;
  overflow-y: auto;
}

.composer__input::placeholder {
  color: var(--ink-4);
}

.composer__input:disabled {
  color: var(--ink-3);
}

.composer__actions {
  flex: none;
  display: flex;
  gap: 6px;
}

.composer__foot {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 7px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.composer__config {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.composer__config-item {
  display: inline-flex;
  gap: 5px;
}

.composer__dot {
  color: var(--ink-4);
}

.composer__latency {
  margin-left: auto;
  flex: none;
  color: var(--ink-4);
}

@media (max-width: 860px) {
  .composer {
    padding: 10px 14px 12px;
  }
}
</style>
