import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import { applyTheme, initialTheme } from './theme.js'
import './styles.css'

// 렌더 전에 테마 먼저 적용 → 첫 화면 깜빡임 방지
applyTheme(initialTheme())

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
