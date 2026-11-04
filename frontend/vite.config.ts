import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: './',
  server: { port: 5173, strictPort: true, proxy: { '/api': 'http://127.0.0.1:17890' } },
  preview: { port: 4173, strictPort: true, proxy: { '/api': 'http://127.0.0.1:17890' } },
  build: { rollupOptions: { output: { manualChunks: { charts: ['echarts'], vue: ['vue'] } } } }
})
