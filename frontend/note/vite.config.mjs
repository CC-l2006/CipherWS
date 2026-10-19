import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    // 端口分配约定：main=8000 / train=8001 / note=8002
    port: 8002,
    // 只有 VITE_API_MODE=http 时才真正发请求；mock 模式不会走到这里。
    // note-gateway 默认 8083（避开 cipherws 的 8080/8081/8082 与 Nacos 的 8848/8849）。
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8083',
        changeOrigin: true
      },
      // 头像等上传文件也走网关（网关对该路径放行、不校验 token），
      // 不加这条的话开发环境下 <img src="/uploads/..."> 会 404
      '/uploads': {
        target: 'http://127.0.0.1:8083',
        changeOrigin: true
      }
    }
  }
})
