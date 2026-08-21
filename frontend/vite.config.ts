import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// dev 서버(5173)에서 /api 호출을 pms 앱(8080)으로 프록시 → 동일 출처라 CORS 설정이 불필요하다
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
