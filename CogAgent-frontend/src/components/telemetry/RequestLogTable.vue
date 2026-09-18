<script setup>
import { ref } from 'vue'
import { findMode } from '@/constants/agent'
import { formatClockSec, formatDateTime, formatMs, formatNumber, shortId } from '@/utils/format'

/**
 * 请求日志
 *
 * 每条记录对应本浏览器发出的一次请求，由前端在请求前后采集。
 * traceId 取自响应体，可以直接拿去后端日志里检索同一次调用。
 */
defineProps({
  samples: { type: Array, default: () => [] },
})

const expandedId = ref(null)

function toggle(id) {
  expandedId.value = expandedId.value === id ? null : id
}

function kindLabel(kind) {
  if (kind === 'chat-stream') return '流式对话'
  if (kind === 'upload') return '文档上传'
  return '同步对话'
}

function statusTag(sample) {
  if (sample.status === 'error') return { cls: 'tag--error', text: '失败' }
  if (sample.status === 'aborted') return { cls: 'tag--warn', text: '已停止' }
  return { cls: 'tag--accent', text: '成功' }
}

</script>

<template>
  <div class="log">
    <table class="table">
      <thead>
        <tr>
          <th class="col-tight">时间</th>
          <th class="col-tight">类型</th>
          <th class="col-tight">所选模式</th>
          <th class="col-num">耗时</th>
          <th class="col-num">Token</th>
          <th class="col-tight">工具</th>
          <th class="col-num">来源</th>
          <th class="col-tight">状态</th>
          <th class="col-tight">traceId</th>
        </tr>
      </thead>
      <tbody>
        <template v-for="sample in samples" :key="sample.id">
          <tr class="log__row" @click="toggle(sample.id)">
            <td class="mono cell-dim">{{ formatClockSec(sample.at) }}</td>
            <td class="cell-dim">{{ kindLabel(sample.kind) }}</td>
            <td>
              <span v-if="sample.kind === 'upload'" class="cell-dim">—</span>
              <span v-else class="tag tag--mono">
                {{ sample.requestedMode ? findMode(sample.requestedMode).label : '自动' }}
              </span>
            </td>
            <td class="col-num">{{ formatMs(sample.latencyMs) }}</td>
            <td class="col-num">
              {{ sample.totalTokens === null ? '—' : formatNumber(sample.totalTokens) }}
            </td>
            <td>
              <span v-if="!sample.tools?.length" class="cell-dim">—</span>
              <span v-else class="mono cell-tools">{{ sample.tools.join('、') }}</span>
            </td>
            <td class="col-num">{{ sample.kind === 'upload' ? '—' : sample.sourceCount }}</td>
            <td>
              <span class="tag" :class="statusTag(sample).cls">{{ statusTag(sample).text }}</span>
            </td>
            <td class="mono cell-dim">
              <span v-if="sample.traceId" :title="sample.traceId">{{ shortId(sample.traceId) }}</span>
              <span v-else>—</span>
            </td>
          </tr>

          <tr v-if="expandedId === sample.id" class="log__detail">
            <td colspan="9">
              <dl class="detail">
                <div>
                  <dt>完整时间</dt>
                  <dd class="mono">{{ formatDateTime(sample.at) }}</dd>
                </div>
                <div class="detail--wide">
                  <dt>traceId</dt>
                  <dd class="mono">{{ sample.traceId || '（该接口不返回 traceId）' }}</dd>
                </div>
                <div v-if="sample.model">
                  <dt>模型</dt>
                  <dd class="mono">{{ sample.model }}</dd>
                </div>
                <div v-if="sample.promptTokens !== null">
                  <dt>Token 明细</dt>
                  <dd class="mono">
                    输入 {{ formatNumber(sample.promptTokens) }} / 输出
                    {{ formatNumber(sample.completionTokens) }} / 合计
                    {{ formatNumber(sample.totalTokens) }}
                  </dd>
                </div>
                <div>
                  <dt>思考步骤</dt>
                  <dd class="mono">{{ sample.stepCount }} 步</dd>
                </div>
                <div v-if="sample.errorMessage" class="detail--wide">
                  <dt>错误信息</dt>
                  <dd class="detail__error">{{ sample.errorMessage }}</dd>
                </div>
              </dl>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.log {
  overflow-x: auto;
}

.log__row {
  cursor: pointer;
}

.cell-dim {
  color: var(--ink-3);
  font-size: var(--fs-xs);
  white-space: nowrap;
}

.cell-tools {
  font-size: var(--fs-xs);
  color: var(--ink-2);
  white-space: nowrap;
}

.log__detail td {
  padding: 13px 12px 16px;
  background: var(--paper);
}

.detail {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px 22px;
}

.detail > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.detail--wide {
  grid-column: 1 / -1;
}

.detail dt {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.detail dd {
  margin: 0;
  font-size: var(--fs-xs);
  color: var(--ink-2);
  word-break: break-all;
}

.detail__error {
  color: var(--rust) !important;
  line-height: 1.65;
}
</style>
