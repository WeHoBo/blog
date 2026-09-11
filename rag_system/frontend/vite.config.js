import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 开发服务器配置
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 将 /api 前缀的请求代理到 FastAPI 后端，并去掉 /api 前缀
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
