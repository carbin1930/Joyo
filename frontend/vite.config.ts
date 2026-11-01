import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  // 相对路径：打包后主窗用 file:// 加载 index.html，绝对路径 /assets 会指到磁盘根目录导致白屏
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 后端只监听 127.0.0.1，前端直接直连，无需代理
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
