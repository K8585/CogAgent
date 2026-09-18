import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// 样式加载顺序有讲究：令牌 → 基础层 → 通用构件，后两者依赖前者定义的变量
import './styles/tokens.css'
import './styles/base.css'
import './styles/components.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

/**
 * 全局错误兜底
 * 组件渲染期抛错时，至少把原因留在控制台，界面不至于静默空白。
 */
app.config.errorHandler = (err, _instance, info) => {
  console.error('[cogagent] 未捕获的组件错误:', info, err)
}

app.mount('#app')
