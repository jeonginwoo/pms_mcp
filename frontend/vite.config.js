import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// dev 서버(5173)에서 /api 호출을 PMS 백엔드(8080)로 프록시 → 동일 출처라 CORS 불필요
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
