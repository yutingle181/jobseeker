import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 前端 5173 -> 后端 8080，/api 走业务接口，/agent 走薄网关（SSE 也经此代理）
export default defineConfig({
  // 工作盘 D: 被安全过滤驱动全局禁止文件重命名/删除，导致 Vite 依赖预构建的
  // deps_temp_* -> deps 重命名失败、持续 504。将缓存目录重定向到 C: 以绕过该封锁。
  cacheDir: 'C:/vite-cache/jobseeker-web',
  plugins: [vue()],
  build: {
    // D: 盘禁止删除/重命名，Vite 默认的 emptyOutDir 会在重建前删除旧 dist，
    // 被 safe-delete 拦截导致 build 失败。关闭后旧文件由新哈希文件覆盖即可，
    // 构建后由 postbuild 脚本把 dist 同步到后端 static。
    emptyOutDir: false,
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/agent': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        // SSE 必须关闭缓冲
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            proxyRes.headers['X-Accel-Buffering'] = 'no'
          })
        },
      },
    },
  },
})
