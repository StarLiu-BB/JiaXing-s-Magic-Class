import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const proxyTarget = process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:19001'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  css: {
    // Vite 5 默认走 Sass 旧版 JS API，会刷屏弃用告警
    preprocessorOptions: { scss: { api: 'modern-compiler' } }
  },
  server: {
    port: 3001,
    strictPort: true,
    proxy: {
      // 与管理端相同：/api 去掉前缀后转给网关，同源访问，不涉及跨域
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // Element Plus 与管理端一致为全量引入（约 790KB，gzip 后约 250KB），单独成块并放宽告警阈值
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('element-plus')) {
            return 'element-plus'
          }
          if (id.includes('highlight.js') || id.includes('markdown-it')) {
            return 'markdown'
          }
          return 'vendor'
        }
      }
    }
  },
  test: {
    environment: 'node',
    include: ['tests/unit/**/*.test.js']
  }
})
