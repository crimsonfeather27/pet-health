import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 后端 static 目录（构建产物输出目标，由 Spring Boot 托管）
const STATIC_DIR = fileURLToPath(
  new URL('../src/main/resources/static', import.meta.url)
)

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 开发期把后端 API 请求代理到本地 Spring Boot（默认 8080）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // 构建产物直接写入后端 static 目录，由 Spring Boot 托管
    outDir: STATIC_DIR,
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500,
  },
})
