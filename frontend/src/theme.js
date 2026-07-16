import { useEffect, useState } from 'react'

const KEY = 'pms.theme'

/** 저장된 선호 → 없으면 OS 설정 → 기본 dark */
export function initialTheme() {
  const saved = localStorage.getItem(KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

export function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme)
}

/** App 최상위에서 한 번만 사용 — 단일 소스로 관리 */
export function useTheme() {
  const [theme, setTheme] = useState(initialTheme)
  useEffect(() => {
    applyTheme(theme)
    localStorage.setItem(KEY, theme)
  }, [theme])
  const toggle = () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))
  return { theme, toggle }
}
