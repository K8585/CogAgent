<script setup>
import { computed, ref, watch } from 'vue'
import MessageItem from './MessageItem.vue'
import { useStickToBottom } from '@/composables/useStickToBottom'
import { EXAMPLE_PROMPTS } from '@/constants/examples'

const props = defineProps({
  conversation: { type: Object, default: null },
  running: { type: Boolean, default: false },
})

const emit = defineEmits(['pick-example'])

const scroller = ref(null)
const { stuck, onScroll, keep, jump } = useStickToBottom(scroller)

const messages = computed(() => props.conversation?.messages || [])

/**
 * 内容签名
 * 消息条数 + 最后一条正文长度 + 状态。
 * 流式生成时正文长度持续变化，签名随之变化，从而驱动自动吸底。
 */
const signature = computed(() => {
  const last = messages.value[messages.value.length - 1]
  return `${messages.value.length}:${last?.content?.length ?? 0}:${last?.status ?? ''}`
})

watch(signature, () => keep())

// 切换会话时直接跳到底部，不做平滑滚动 —— 那会让用户误以为内容在动
watch(
  () => props.conversation?.id,
  () => jump()
)
</script>

<template>
  <div ref="scroller" class="thread" @scroll.passive="onScroll">
    <!-- —————————————— 空状态 —————————————— -->
    <div v-if="!messages.length" class="thread__empty">
      <h2>还没有消息</h2>
      <p class="thread__empty-lead">
        在下面输入问题即可开始。右侧「运行参数」决定了这次请求会走哪条编排路径。
        下面几个问题正好覆盖后端的四种 Agent 模式，适合第一次跑通时逐条试。
      </p>

      <ul class="examples">
        <li v-for="ex in EXAMPLE_PROMPTS" :key="ex.label">
          <button type="button" class="example" @click="emit('pick-example', ex.text)">
            <span class="example__top">
              <span class="example__label">{{ ex.label }}</span>
              <span class="example__expect mono">{{ ex.expect }}</span>
            </span>
            <span class="example__text">{{ ex.text }}</span>
          </button>
        </li>
      </ul>
    </div>

    <!-- —————————————— 消息流（时间轴） —————————————— -->
    <div v-else class="thread__list">
      <MessageItem
        v-for="(msg, i) in messages"
        :key="msg.id"
        :message="msg"
        :is-last="i === messages.length - 1"
      />
    </div>

    <!-- 用户向上翻阅时，给一个回到底部的入口，而不是强行拽回去 -->
    <Transition name="fade">
      <button v-if="!stuck && messages.length" type="button" class="thread__jump" @click="jump">
        回到最新
      </button>
    </Transition>
  </div>
</template>

<style scoped>
.thread {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 26px;
}

/* 贯穿整个消息流的纵向轴线：位于沟槽与内容之间 */
.thread__list {
  position: relative;
  padding: 6px 0 22px;
}

.thread__list::before {
  content: '';
  position: absolute;
  /* 与 MessageItem 的沟槽宽度(52px) + 间距(18px) 对齐，落在两者之间 */
  left: 61px;
  top: 0;
  bottom: 22px;
  width: 1px;
  background: var(--rule);
}

/* —————————————— 空状态 —————————————— */
.thread__empty {
  max-width: 62ch;
  padding: 52px 0 40px;
}

.thread__empty h2 {
  font-size: var(--fs-xl);
  margin-bottom: 8px;
}

.thread__empty-lead {
  font-size: var(--fs-md);
  line-height: 1.75;
  color: var(--ink-2);
  margin-bottom: 24px;
}

.examples {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--rule);
}

.example {
  display: block;
  width: 100%;
  padding: 11px 2px;
  border-bottom: 1px solid var(--rule);
  text-align: left;
  transition: background-color 0.12s var(--ease);
}

.example:hover {
  background: var(--surface);
}

.example__top {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 3px;
}

.example__label {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--celadon-deep);
}

.example__expect {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.example__text {
  display: block;
  font-size: var(--fs-md);
  line-height: 1.65;
  color: var(--ink-2);
}

/* —————————————— 回到底部 —————————————— */
.thread__jump {
  position: sticky;
  bottom: 14px;
  left: 50%;
  transform: translateX(-50%);
  padding: 5px 13px;
  border: 1px solid var(--rule-strong);
  border-radius: 999px;
  background: var(--surface);
  box-shadow: var(--shadow-pop);
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

.thread__jump:hover {
  color: var(--ink);
  border-color: var(--ink-3);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.16s var(--ease);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 860px) {
  .thread {
    padding: 0 14px;
  }
  /* 轴线位置必须与 MessageItem 的沟槽宽度保持一致，否则节点会脱离轴线 */
}
</style>
