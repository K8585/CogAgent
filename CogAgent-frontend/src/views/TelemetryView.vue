<script setup>
import { computed, ref } from 'vue'
import RequestLogTable from '@/components/telemetry/RequestLogTable.vue'
import { useTelemetryStore } from '@/stores/telemetry'
import { findMode } from '@/constants/agent'
import { formatMs, formatNumber, formatDateTime } from '@/utils/format'

const telemetry = useTelemetryStore()

const stats = computed(() => telemetry.stats)
const hasData = computed(() => telemetry.samples.length > 0)

const confirmingClear = ref(false)

/** 分布条的宽度按最大值归一，样本量小时也不会画出一条满格 */
function barWidth(count, list) {
  const max = Math.max(...list.map((x) => x.count), 1)
  return `${Math.round((count / max) * 100)}%`
}

const successRateText = computed(() => {
  const r = stats.value.successRate
  if (r === null) return '—'
  return `${(r * 100).toFixed(r === 1 || r === 0 ? 0 : 1)}%`
})
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>运行观测</h1>
      <p>
        每次对话的耗时、token、命中的工具与检索来源都会在这里留下一条记录。
        traceId 取自后端响应，可以拿去服务端日志里检索同一次调用的完整 Span 链路。
      </p>
    </header>

    <!--
      数据来源必须前置声明，而不是藏在角落。
      这份面板统计的是「这个浏览器发出的请求」，不是服务端的全量流量。
    -->
    <div class="notice notice--info source-note">
      <div>
        <strong>这些数据来自本浏览器，不是服务端。</strong>
        后端目前没有对外暴露 trace 查询接口（TraceService 只写服务端日志），
        因此本页指标全部由前端在请求前后采集，保存在 localStorage，最多保留最近 300 条。
        另外，当「运行参数」选择自动路由时，后端实际选中了哪个模式不会出现在响应体里
        （ChatResponse 无 mode 字段），所以「所选模式」一栏记录的是你的选择，不是服务端的判定结果。
      </div>
    </div>

    <!-- ———————————————— 指标 ———————————————— -->
    <section class="metrics">
      <div class="metric">
        <span class="metric__value mono">{{ formatNumber(stats.total) }}</span>
        <span class="metric__label">对话请求</span>
      </div>
      <div class="metric">
        <span class="metric__value mono" :class="{ 'is-bad': stats.failed > 0 }">{{ successRateText }}</span>
        <span class="metric__label">成功率（{{ stats.ok }}/{{ stats.total }}）</span>
      </div>
      <div class="metric">
        <span class="metric__value mono">{{ formatMs(stats.p50) }}</span>
        <span class="metric__label">P50 耗时</span>
      </div>
      <div class="metric">
        <span class="metric__value mono">{{ formatMs(stats.p95) }}</span>
        <span class="metric__label">P95 耗时</span>
      </div>
      <div class="metric">
        <span class="metric__value mono">{{ formatNumber(stats.tokens) }}</span>
        <span class="metric__label">累计 Token</span>
      </div>
      <div class="metric">
        <span class="metric__value mono">{{ stats.ragHitRate === null ? '—' : `${Math.round(stats.ragHitRate * 100)}%` }}</span>
        <span class="metric__label">检索命中率</span>
      </div>
    </section>

    <p v-if="stats.lastAt" class="metrics__foot mono">
      最近一次记录：{{ formatDateTime(stats.lastAt) }} · 样本上限 300 条
    </p>

    <!-- ———————————————— 分布 ———————————————— -->
    <div v-if="hasData" class="grid">
      <section class="panel">
        <div class="panel__head">
          <h2 class="panel__title">所选模式分布</h2>
          <span class="panel__note">按你的选择统计</span>
        </div>
        <ul v-if="stats.modeDistribution.length" class="dist">
          <li v-for="item in stats.modeDistribution" :key="item.mode || 'auto'" class="dist__row">
            <span class="dist__label">{{ item.mode ? findMode(item.mode).label : '自动路由' }}</span>
            <span class="dist__track">
              <span class="dist__fill" :style="{ width: barWidth(item.count, stats.modeDistribution) }" />
            </span>
            <span class="dist__count mono">{{ item.count }}</span>
          </li>
        </ul>
        <p v-else class="empty">暂无数据</p>
      </section>

      <section class="panel">
        <div class="panel__head">
          <h2 class="panel__title">工具调用次数</h2>
          <span class="panel__note">来自响应体的 usedTools</span>
        </div>
        <ul v-if="stats.toolRanking.length" class="dist">
          <li v-for="item in stats.toolRanking" :key="item.name" class="dist__row">
            <span class="dist__label mono">{{ item.name }}</span>
            <span class="dist__track">
              <span class="dist__fill dist__fill--muted" :style="{ width: barWidth(item.count, stats.toolRanking) }" />
            </span>
            <span class="dist__count mono">{{ item.count }}</span>
          </li>
        </ul>
        <p v-else class="empty">
          还没有工具被调用过。试试 ReAct 模式下问一个算术题。
        </p>
      </section>
    </div>

    <!-- ———————————————— 日志 ———————————————— -->
    <section class="panel">
      <div class="panel__head">
        <h2 class="panel__title">请求日志</h2>
        <div class="panel__actions">
          <span class="panel__note">点击任意一行查看明细</span>
          <template v-if="hasData">
            <template v-if="confirmingClear">
              <button type="button" class="btn btn--sm btn--danger" @click="telemetry.clear(); confirmingClear = false">
                确认清空
              </button>
              <button type="button" class="btn btn--sm btn--quiet" @click="confirmingClear = false">取消</button>
            </template>
            <button v-else type="button" class="btn btn--sm btn--quiet btn--danger" @click="confirmingClear = true">
              清空
            </button>
          </template>
        </div>
      </div>

      <div v-if="!hasData" class="empty">
        <p class="empty__title">还没有观测数据</p>
        <p class="empty__hint">
          去「对话」页发一条消息，或到「知识库」上传一个文档，这里就会开始记录。
          记录保存在本浏览器，不会上报到服务端。
        </p>
      </div>

      <RequestLogTable v-else :samples="telemetry.recent" />
    </section>
  </div>
</template>

<style scoped>
.page {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 30px 26px 56px;
}

.page > * + * {
  margin-top: 20px;
}

.page__head {
  max-width: 78ch;
}

.page__head h1 {
  font-size: var(--fs-3xl);
  letter-spacing: -0.02em;
  margin-bottom: 10px;
}

.page__head p {
  color: var(--ink-2);
  line-height: 1.75;
}

.source-note strong {
  font-weight: 600;
}

/* ———————————————— 指标 ———————————————— */
.metrics {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--surface);
  overflow: hidden;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 15px 18px;
  border-right: 1px solid var(--rule);
}

.metric:last-child {
  border-right: none;
}

.metric__value {
  font-size: var(--fs-2xl);
  font-weight: 600;
  line-height: 1.25;
  letter-spacing: -0.01em;
}

.metric__value.is-bad {
  color: var(--rust);
}

.metric__label {
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.metrics__foot {
  margin-top: 8px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

/* ———————————————— 分布 ———————————————— */
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  align-items: start;
}

.dist {
  padding: 12px 18px 16px;
}

.dist__row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 5px 0;
}

.dist__label {
  flex: none;
  width: 92px;
  font-size: var(--fs-md);
  color: var(--ink-2);
}

.dist__track {
  flex: 1;
  min-width: 0;
  height: 6px;
  background: var(--surface-sunk);
  border-radius: 1px;
  overflow: hidden;
}

.dist__fill {
  display: block;
  height: 100%;
  background: var(--celadon);
  transition: width 0.3s var(--ease);
}

.dist__fill--muted {
  background: var(--ink-3);
}

.dist__count {
  flex: none;
  width: 36px;
  text-align: right;
  font-size: var(--fs-md);
  color: var(--ink);
}

.dist .empty {
  padding: 24px 18px;
  text-align: left;
}

.panel__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 1100px) {
  .metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .metric:nth-child(3) {
    border-right: none;
  }
  .metric:nth-child(-n + 3) {
    border-bottom: 1px solid var(--rule);
  }
  .grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 860px) {
  .page {
    padding: 22px 14px 44px;
  }
  .page__head h1 {
    font-size: var(--fs-2xl);
  }
  .metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .metric:nth-child(3) {
    border-right: 1px solid var(--rule);
  }
  .metric:nth-child(2n) {
    border-right: none;
  }
  .metric:nth-child(-n + 4) {
    border-bottom: 1px solid var(--rule);
  }
}
</style>
