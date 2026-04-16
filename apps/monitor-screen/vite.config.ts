import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5174,
    proxy: {
      // SSE 长连接直连 compare-dashboard-service（网关不支持流式响应）
      '/api/v1/dashboard': {
        target: 'http://localhost:8087',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8079',
        changeOrigin: true,
      },
      '/minio-frames': {
        target: 'http://localhost:9000',
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/minio-frames/, ''),
      },
    },
  },
})
