import type { Theme } from '../theme'

interface Props {
  theme: Theme
  onToggle: () => void
}

export default function ThemeToggle({ theme, onToggle }: Props) {
  const dark = theme === 'dark'

  return (
    <button
      className="theme-toggle btn btn-ghost"
      onClick={onToggle}
      title={dark ? '라이트 모드로 전환' : '다크 모드로 전환'}
      aria-label="테마 전환"
    >
      {dark ? '☀️' : '🌙'}
    </button>
  )
}
