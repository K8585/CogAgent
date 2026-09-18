<script setup>
import { computed, ref } from 'vue'
import { useConversationsStore } from '@/stores/conversations'
import { formatClock, formatDay } from '@/utils/format'

/**
 * 会话栏
 *
 * 【为什么会话在这里而不是服务端】
 * 后端没有会话列表 / 历史查询接口（MemoryManager 的 getHistory 未对外暴露 HTTP），
 * 只有 Redis 里的短期记忆。所以这份列表完全存在浏览器 localStorage 中。
 *
 * 一个直接后果值得在界面上讲清楚：换台电脑打开是空列表，
 * 但服务端其实还记得这段对话 —— 因为 conversationId 是前端生成后传给后端的。
 */
const conversations = useConversationsStore()

/** 正在等待确认删除的会话 id —— 删除不可撤销，先问一句 */
const confirmingId = ref(null)

const items = computed(() => conversations.sorted)

function onDelete(id) {
  conversations.remove(id)
  confirmingId.value = null
}
</script>

<template>
  <aside class="rail">
    <div class="rail__head">
      <h2 class="rail__title">会话</h2>
      <button type="button" class="btn btn--sm btn--quiet" @click="conversations.createConversation()">
        新建
      </button>
    </div>

    <div class="rail__list" role="list">
      <p v-if="!items.length" class="rail__empty">
        还没有会话。发第一条消息时会自动创建，也可以点上面的「新建」。
      </p>

      <div
        v-for="conv in items"
        :key="conv.id"
        class="item"
        :class="{ 'is-active': conv.id === conversations.activeId }"
        role="listitem"
      >
        <button
          type="button"
          class="item__main"
          :aria-current="conv.id === conversations.activeId ? 'true' : undefined"
          @click="conversations.select(conv.id)"
        >
          <span class="item__title">{{ conv.title }}</span>
          <span class="item__meta">
            <span class="mono">{{ formatDay(conv.updatedAt) }} {{ formatClock(conv.updatedAt) }}</span>
            <span class="item__count mono">{{ conv.messages.length }} 条</span>
          </span>
        </button>

        <!-- 二段式删除：点「删」展开确认，避免误触清掉一段对话 -->
        <div v-if="confirmingId === conv.id" class="item__confirm">
          <span>删除这段会话？</span>
          <button type="button" class="btn btn--sm btn--danger" @click="onDelete(conv.id)">删除</button>
          <button type="button" class="btn btn--sm btn--quiet" @click="confirmingId = null">取消</button>
        </div>
        <button
          v-else
          type="button"
          class="item__del"
          :aria-label="`删除会话 ${conv.title}`"
          @click="confirmingId = conv.id"
        >
          删
        </button>
      </div>
    </div>

    <p class="rail__note">
      会话列表保存在本浏览器。对话正文与
      <code class="mono">conversationId</code>
      都已同步给后端，服务端记忆不受影响。
    </p>

    <div v-if="items.length" class="rail__foot">
      <button type="button" class="btn btn--sm btn--quiet btn--danger" @click="conversations.clearAll()">
        清空全部会话
      </button>
    </div>
  </aside>
</template>

<style scoped>
.rail {
  display: flex;
  flex-direction: column;
  width: var(--rail-w);
  flex: none;
  border-right: 1px solid var(--rule);
  background: var(--surface);
}

.rail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 8px 10px 14px;
  border-bottom: 1px solid var(--rule);
}

.rail__title {
  font-size: var(--fs-md);
  font-weight: 600;
}

.rail__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 0;
}

.rail__empty {
  padding: 18px 14px;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--ink-3);
}

/* —— 单条会话 —— */
.item {
  position: relative;
  border-left: 2px solid transparent;
}

.item:hover {
  background: var(--paper);
}

.item.is-active {
  background: var(--celadon-mist);
  border-left-color: var(--celadon);
}

.item__main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 9px 30px 9px 12px;
  text-align: left;
}

.item__title {
  font-size: var(--fs-md);
  color: var(--ink);
  /* 标题只占一行，超出省略，保证列表的行高节奏一致 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item__meta {
  display: flex;
  gap: 8px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.item.is-active .item__meta {
  color: var(--celadon-deep);
  opacity: 0.75;
}

.item__count {
  margin-left: auto;
}

/* 删除入口：默认隐去，悬停或聚焦时才出现，避免列表被按钮噪声填满 */
.item__del {
  position: absolute;
  top: 7px;
  right: 6px;
  padding: 2px 5px;
  border-radius: var(--radius-xs);
  font-size: var(--fs-xs);
  color: var(--ink-3);
  opacity: 0;
  transition: opacity 0.12s var(--ease), color 0.12s var(--ease);
}

.item:hover .item__del,
.item__del:focus-visible {
  opacity: 1;
}

.item__del:hover {
  color: var(--rust);
  background: var(--surface);
}

.item__confirm {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px 9px 12px;
  font-size: var(--fs-xs);
  color: var(--ink-2);
}

.item__confirm .btn {
  height: 22px;
  padding: 0 7px;
}

/* —— 底部说明 —— */
.rail__note {
  padding: 10px 14px;
  border-top: 1px solid var(--rule);
  font-size: var(--fs-xs);
  line-height: 1.65;
  color: var(--ink-3);
}

.rail__note code {
  padding: 1px 3px;
  border-radius: var(--radius-xs);
  background: var(--surface-sunk);
}

.rail__foot {
  padding: 0 8px 10px 8px;
}

.rail__foot .btn {
  width: 100%;
}
</style>
