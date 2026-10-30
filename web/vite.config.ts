import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 后端没有统一网关，dev 直连 8080；生产由 Nginx 反代同一路径。
      // 前端代码里因此永远只写相对路径 `/api/...`，不出现 host 与端口。
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
