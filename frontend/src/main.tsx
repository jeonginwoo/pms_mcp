import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { applyTheme, initialTheme } from './theme'
import './styles.css'

// 렌더 전에 테마 먼저 적용 → 첫 화면 깜빡임 방지
applyTheme(initialTheme())

const root = document.getElementById('root')

if (!root) {
  throw new Error('#root 엘리먼트가 없습니다')
}

createRoot(root).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
