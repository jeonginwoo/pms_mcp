import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// dev 서버(5173)에서 /api 호출을 pms 앱(8080)으로 프록시 → 동일 출처라 CORS 설정이 불필요하다
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      /*
       * 챗만 host 앱(8081)으로 간다 — **임시 배선이다.**
       * 정본은 pms의 chat BFF `POST /api/chat`(PRD-pms §7)이고 그것이 서면 이 항목만
       * 지우면 된다. 화면·api.ts는 이미 정본 경로로 부르고 있어 바뀌지 않는다.
       *
       * '/api'보다 **먼저 와야 한다**: vite는 등록 순서로 접두사를 맞춰 첫 항목이 이긴다.
       * host는 이 라우트를 `/chat`으로 열고 있어 rewrite로 접두사를 벗긴다.
       */
      '/api/chat': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/chat/, '/chat'),
      },
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
