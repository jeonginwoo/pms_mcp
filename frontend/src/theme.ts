/*
 * 테마 — styles.css의 :root[data-theme] 팔레트를 토글한다 (구 theme.js 이식).
 */
import { useEffect, useState } from 'react'

const KEY = 'pms.theme'

export type Theme = 'light' | 'dark'

/** 저장된 선호 → 없으면 OS 설정 → 기본 dark */
export function initialTheme(): Theme {
  const saved = localStorage.getItem(KEY)

  if (saved === 'light' || saved === 'dark') {
    return saved
  }

  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

export function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute('data-theme', theme)
}

/** App 최상위에서 한 번만 사용 — 단일 소스로 관리 */
export function useTheme(): { theme: Theme; toggle: () => void } {
  const [theme, setTheme] = useState<Theme>(initialTheme)

  useEffect(() => {
    applyTheme(theme)
    localStorage.setItem(KEY, theme)
  }, [theme])

  return { theme, toggle: () => setTheme((t) => (t === 'dark' ? 'light' : 'dark')) }
}
