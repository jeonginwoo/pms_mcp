import { useEffect, useState } from 'react'
import { api } from '../api.js'
import type { HealthStatus } from '../types/HealthStatus'

/** FE→BE→DB 연결 상태 배지. 로그인 화면에서 백엔드·DB가 살아 있는지 보여준다. */
interface Props {
  /** 재확인 주기(ms). 0이면 마운트 시 1회만 조회 */
  intervalMs?: number
}

type Phase = 'checking' | 'up' | 'down'

const LABEL: Record<Phase, string> = {
  checking: '서버 연결 확인 중…',
  up: '서버·DB 연결됨',
  down: '서버 또는 DB에 연결할 수 없습니다',
}

const COLOR: Record<Phase, string> = {
  checking: 'var(--muted2)',
  up: '#16a34a',
  down: '#dc2626',
}

export default function HealthBadge({ intervalMs = 0 }: Props) {
  const [phase, setPhase] = useState<Phase>('checking')

  useEffect(() => {
    let alive = true

    const check = async () => {
      try {
        const health: HealthStatus = await api.health()
        if (alive) { setPhase(health.status === 'UP' && health.database ? 'up' : 'down') }
      } catch {
        if (alive) { setPhase('down') }
      }
    }

    check()
    const timer = intervalMs > 0 ? setInterval(check, intervalMs) : null

    return () => {
      alive = false
      if (timer) clearInterval(timer)
    }
  }, [intervalMs])

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 12 }}>
      <span style={{ width: 8, height: 8, borderRadius: '50%', background: COLOR[phase], flex: 'none' }} />
      <span className="muted2" style={{ fontSize: 12, color: COLOR[phase] }}>{LABEL[phase]}</span>
    </div>
  )
}
