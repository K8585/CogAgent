import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 路由
 *
 * 使用 hash 模式（createWebHashHistory）而非 history 模式：
 * 前端是纯静态产物，hash 模式不需要后端配置 fallback 重写，
 * 直接把 dist 丢给 Nginx / 任意静态服务器都能跑，省掉一类部署问题。
 */
const routes = [
  {
    path: '/',
    name: 'overview',
    component: () => import('@/views/OverviewView.vue'),
    meta: { title: '平台概览' },
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { title: '对话工作台' },
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: () => import('@/views/KnowledgeView.vue'),
    meta: { title: '知识库' },
  },
  {
    path: '/telemetry',
    name: 'telemetry',
    component: () => import('@/views/TelemetryView.vue'),
    meta: { title: '运行观测' },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.afterEach((to) => {
  const title = to.meta?.title
  document.title = title ? `${title} · CogAgent 控制台` : 'CogAgent 控制台'
})

export default router
