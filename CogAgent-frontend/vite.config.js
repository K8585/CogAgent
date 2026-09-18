import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 配置
 *
 * 跨域策略：
 *   后端（CogAgent-trigger）没有配置任何 CORS 策略，浏览器直连 localhost:8080 会被
 *   同源策略拦截。开发期一律通过下面的 devServer.proxy 走同源代理，浏览器只会看到
 *   对 localhost:5173 的请求，不产生跨域。
 *   生产部署时把前端静态资源与后端放在同一域名下，或设置 VITE_API_BASE_URL 指到
 *   后端地址并由后端 / 网关补齐 CORS 响应头（见 README「跨域」一节）。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    server: {
      port: 5173,
      strictPort: false,
      // 便于在局域网内用手机验证响应式布局
      host: true,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          // SSE（/api/v1/chat/stream）需要代理不缓冲、不超时中断，
          // http-proxy 默认对流式响应是透传的，这里显式关闭压缩即可。
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => {
              proxyReq.setHeader('Accept-Encoding', 'identity')
            })
          },
        },
        // 连通探测走 actuator 健康端点。与 /api 分开配置：它不涉及 SSE，
        // 不需要上面那套缓冲相关设置；生产环境的 Nginx 也要单独加一条反代规则。
        '/actuator': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },

    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 900,
    },
  }
})
