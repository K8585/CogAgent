<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ConversationRail from '@/components/chat/ConversationRail.vue'
import MessageThread from '@/components/chat/MessageThread.vue'
import ChatComposer from '@/components/chat/ChatComposer.vue'
import RunSettings from '@/components/chat/RunSettings.vue'
import { useConversationsStore } from '@/stores/conversations'
import { useSettingsStore } from '@/stores/settings'
import { AGENT_MODES } from '@/constants/agent'

const route = useRoute()
const conversations = useConversationsStore()
const settings = useSettingsStore()

/** 窄屏下的抽屉开关；宽屏由 CSS 强制常显，这两个状态不起作用 */
const showRail = ref(false)
const showAside = ref(false)

onMounted(() => {
  // 从概览页点模式行跳过来时会带上 ?mode=REACT，直接替用户选好
  const mode = route.query.mode
  if (typeof mode === 'string' && AGENT_MODES.some((m) => m.value === mode)) {
    settings.setMode(mode)
  }
})

const active = computed(() => conversations.active)

// 选中会话后收起抽屉（仅在窄屏下有意义，宽屏时该面板本来就常显）
watch(
  () => conversations.activeId,
  () => {
    showRail.value = false
  }
)

/** 最近一次请求的耗时，显示在输入框下方作为手感参考 */
const lastLatency = computed(() => {
  const msgs = active.value?.messages || []
  for (let i = msgs.length - 1; i >= 0; i -= 1) {
    if (msgs[i].role === 'assistant' && msgs[i].latencyMs !== null) return msgs[i].latencyMs
  }
  return null
})

function onSend(text) {
  conversations.ensureActive()
  conversations.send(text)
  showRail.value = false
}

function onPickExample(text) {
  onSend(text)
}
</script>

<template>
  <div class="chat">
    <!-- ———————————————— 左侧：会话 ———————————————— -->
    <div class="chat__panel chat__panel--rail" :class="{ 'is-open': showRail }">
      <ConversationRail />
    </div>

    <!-- ———————————————— 中间：消息流 ———————————————— -->
    <section class="chat__main">
      <header class="chat__bar">
        <button
          type="button"
          class="chat__toggle chat__toggle--rail"
          :aria-expanded="showRail"
          @click="showRail = !showRail"
        >
          会话
        </button>

        <h1 class="chat__title">{{ active?.title || '新会话' }}</h1>

        <span class="chat__sub mono">
          {{ active ? `${active.messages.length} 条` : '未开始' }}
        </span>

        <button
          type="button"
          class="chat__toggle chat__toggle--aside"
          :aria-expanded="showAside"
          @click="showAside = !showAside"
        >
          参数
        </button>
      </header>

      <MessageThread
        :conversation="active"
        :running="conversations.running"
        @pick-example="onPickExample"
      />

      <ChatComposer
        :running="conversations.running"
        :last-latency-ms="lastLatency"
        @send="onSend"
        @stop="conversations.abort()"
      />
    </section>

    <!-- ———————————————— 右侧：运行参数 ———————————————— -->
    <div class="chat__panel chat__panel--aside" :class="{ 'is-open': showAside }">
      <RunSettings />
    </div>

    <!-- 窄屏抽屉的遮罩 -->
    <div
      v-if="showRail || showAside"
      class="chat__scrim"
      @click="showRail = false; showAside = false"
    />
  </div>
</template>

<style scoped>
.chat {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

.chat__panel {
  display: flex;
  flex: none;
}

.chat__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--paper);
}

/* ———————————————— 消息区顶栏 ———————————————— */
.chat__bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 26px;
  border-bottom: 1px solid var(--rule);
  background: var(--paper);
}

.chat__title {
  font-size: var(--fs-md);
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat__sub {
  flex: none;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.chat__toggle {
  display: none;
  padding: 3px 9px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

.chat__toggle--aside {
  margin-left: auto;
}

.chat__scrim {
  display: none;
}

/* ———————————————— 响应式 ———————————————— */

/* 运行参数栏：1240px 以下收进抽屉 */
@media (max-width: 1240px) {
  .chat__panel--aside {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 30;
    display: none;
    box-shadow: var(--shadow-pop);
  }
  .chat__panel--aside.is-open {
    display: flex;
  }
  .chat__toggle--aside {
    display: inline-block;
  }
}

/* 会话栏：980px 以下收进抽屉 */
@media (max-width: 980px) {
  .chat__panel--rail {
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 30;
    display: none;
    box-shadow: var(--shadow-pop);
  }
  .chat__panel--rail.is-open {
    display: flex;
  }
  .chat__toggle--rail {
    display: inline-block;
  }
}

/* 抽屉打开时的遮罩 */
@media (max-width: 1240px) {
  .chat__scrim {
    display: block;
    position: absolute;
    inset: 0;
    z-index: 20;
    background: rgba(23, 26, 28, 0.22);
  }
}

@media (max-width: 860px) {
  .chat__bar {
    padding: 0 14px;
  }
}
</style>
