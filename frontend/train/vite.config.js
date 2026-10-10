import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    // 端口分配约定：frontend/ 下的子项目按 8000 + 序号顺延
    //   main = 8000，train = 8001，以后新增的依次 8002 / 8003 …
    // 避开 8080（网关）、8081/8082（后端服务）、8848/8849（Nacos）。
    port: 8001
  }
})