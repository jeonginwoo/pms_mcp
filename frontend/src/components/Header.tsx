/*
 * 상단 바 — 화면 제목 + AI 어시스턴트 + 알림 벨 + 새로고침 + 테마.
 * 어시스턴트는 2026-08-27에 붙었다: 정본 BFF(`/api/chat`)는 아직 없고 host 앱의 대역(8081)에
 * vite 프록시로 임시로 닿는다 — 임시라는 사실과 그 대가는 api.ts `chat`이 적고 있다.
 * 알림 벨은 2026-08-24에 붙었다 — 서버 F1-3이 섰다.
 */
import { useStore } from '../store'
import type { Route } from '../store'
import CallerSwitcher from './CallerSwitcher'
import NotificationBell from './NotificationBell'
import ThemeToggle from './ThemeToggle'
import type { Theme } from '../theme'

const TITLES: Record<Route, string> = {
  home: '홈',
  sales: '영업',
  projects: '프로젝트',
  people: '인력 · 조직',
  utilization: '가동률',
  maintenance: '유지보수 계약',
  issues: '유지보수 이슈',
  audit: '감사 로그',
}

interface Props {
  theme: Theme
  onToggleTheme: () => void
  chatOpen: boolean
  onToggleChat: () => void
}

export default function Header({ theme, onToggleTheme, chatOpen, onToggleChat }: Props) {
  const { route, reload, sessionMode } = useStore()

  return (
    <header className="topbar">
      <div className="page-title">{TITLES[route]}</div>
      {sessionMode === 'caller' && <CallerSwitcher />}
      <button className={chatOpen ? 'btn btn-primary' : 'btn btn-ghost'} onClick={onToggleChat}>
        AI 어시스턴트
      </button>
      <NotificationBell />
      <button className="btn btn-ghost" onClick={() => void reload()}>새로고침</button>
      <ThemeToggle theme={theme} onToggle={onToggleTheme} />
    </header>
  )
}
