<script setup>
import { RouterLink, RouterView } from 'vue-router'
import ConnectionBadge from '@/components/ConnectionBadge.vue'

/**
 * 应用外壳
 *
 * 导航放在顶栏而不是左侧竖栏：本应用只有四个页面，用竖栏会占用一大块
 * 永久性的横向空间，而执行轨迹这类信息恰恰是横向铺开更好读的。
 * 顶栏导航用一条 2px 下划线标记当前位置，不做胶囊背景。
 */
const navItems = [
  { name: 'overview', label: '概览' },
  { name: 'chat', label: '对话' },
  { name: 'knowledge', label: '知识库' },
  { name: 'telemetry', label: '运行观测' },
]
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <RouterLink to="/" class="brand">
        <svg class="brand__mark" viewBox="0 0 32 32" aria-hidden="true">
          <!-- 用字面色值而非 var()：SVG 表现属性里的 CSS 变量在部分浏览器上解析不稳定 -->
          <rect width="32" height="32" rx="7" fill="#10635A" />
          <circle cx="16" cy="16" r="7.5" fill="none" stroke="#F3F4F2" stroke-width="1.5" />
          <circle cx="16" cy="16" r="2.6" fill="#F3F4F2" />
          <path
            d="M16 4.5v4M16 23.5v4M4.5 16h4M23.5 16h4"
            stroke="#F3F4F2"
            stroke-width="1.5"
            stroke-linecap="round"
          />
        </svg>
        <span class="brand__name">CogAgent</span>
        <span class="brand__suffix">控制台</span>
      </RouterLink>

      <nav class="nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          class="nav__link"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="topbar__right">
        <ConnectionBadge />
      </div>
    </header>

    <main class="viewport">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 30px;
  height: var(--topbar-h);
  flex: none;
  padding: 0 18px;
  border-bottom: 1px solid var(--rule);
  background: var(--surface);
}

/* —— 品牌 —— */
.brand {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  color: var(--ink);
  text-decoration: none;
}

.brand:hover {
  text-decoration: none;
}

.brand__mark {
  width: 18px;
  height: 18px;
  flex: none;
  /* 与文字基线对齐，而不是被 flex 拉伸 */
  align-self: center;
}

.brand__name {
  font-size: var(--fs-lg);
  font-weight: 600;
  letter-spacing: -0.015em;
}

.brand__suffix {
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

/* —— 导航 —— */
.nav {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 100%;
}

.nav__link {
  display: inline-flex;
  align-items: center;
  height: 100%;
  padding: 0 11px;
  border-bottom: 2px solid transparent;
  color: var(--ink-2);
  font-size: var(--fs-md);
  text-decoration: none;
  transition: color 0.12s var(--ease), border-color 0.12s var(--ease);
}

.nav__link:hover {
  color: var(--ink);
  text-decoration: none;
}

.nav__link.router-link-exact-active {
  color: var(--celadon-deep);
  border-bottom-color: var(--celadon);
}

.topbar__right {
  margin-left: auto;
}

/* —— 内容区 —— */
.viewport {
  flex: 1;
  min-height: 0;
  display: flex;
}

@media (max-width: 720px) {
  .topbar {
    gap: 14px;
    padding: 0 12px;
  }
  .brand__suffix {
    display: none;
  }
  .nav__link {
    padding: 0 8px;
  }
}
</style>
