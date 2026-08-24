/*
 * 상단 바 — 화면 제목 + 새로고침 + 테마.
 * AI 어시스턴트 패널과 알림 벨은 챗 BFF(`/api/chat`)·알림 모듈이 생기면 돌아온다
 * (지금 두면 눌러도 아무 일이 없는 버튼이 된다).
 */
import { useStore } from '../store'
import type { Route } from '../store'
import CallerSwitcher from './CallerSwitcher'
import ThemeToggle from './ThemeToggle'
import type { Theme } from '../theme'

const TITLES: Record<Route, string> = {
  home: '홈',
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
}

export default function Header({ theme, onToggleTheme }: Props) {
  const { route, reload, sessionMode } = useStore()

  return (
    <header className="topbar">
      <div className="page-title">{TITLES[route]}</div>
      {sessionMode === 'caller' && <CallerSwitcher />}
      <button className="btn btn-ghost" onClick={() => void reload()}>새로고침</button>
      <ThemeToggle theme={theme} onToggle={onToggleTheme} />
    </header>
  )
}
