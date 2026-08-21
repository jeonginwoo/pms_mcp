/*
 * 화자 전환 (개발 모드 전용) — 로그인 없이 들어온 세션에서만 보인다.
 *
 * 왜 필요한가: 가시성·권한은 화자에 따라 갈리는데(관리자=전사 · 팀장·팀원=팀),
 * 그 차이를 확인하려면 로그아웃·재로그인 없이 화자를 바꿀 수 있어야 한다.
 *
 * 목록은 **지금까지 본 인원의 누적 명부**다(가시성 목록이 아니다) — 가시성 좁은
 * 화자로 전환하면 보이는 인원이 줄어들어, 그 목록만 쓰면 되돌아갈 수 없다.
 * 명부에 없는 사람으로 가려면 personId를 직접 넣는다.
 */
import { useState } from 'react'
import { useStore } from '../store'

export default function CallerSwitcher() {
  const { me, roster, enterAsCaller } = useStore()
  const [manualId, setManualId] = useState('')
  const [busy, setBusy] = useState(false)

  const switchTo = async (personId: number) => {
    if (busy || !Number.isFinite(personId) || personId <= 0 || personId === me?.id) {
      return
    }

    setBusy(true)

    try {
      await enterAsCaller(personId)
      setManualId('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span className="badge" style={{ background: 'var(--confirm-bg)', color: 'var(--confirm-text)', border: '1px solid var(--confirm-border)' }}>
        개발 모드 · 헤더 화자
      </span>
      <select value={me?.id ?? ''} disabled={busy}
        onChange={(e) => void switchTo(Number(e.target.value))}
        style={{ maxWidth: 220 }}>
        {roster.length === 0 && <option value={me?.id ?? ''}>{me?.name ?? '—'}</option>}
        {roster.map((person) => (
          <option key={person.id} value={person.id}>
            {person.name} · {person.orgUnit}
          </option>
        ))}
      </select>
      <input type="number" min="1" placeholder="id" value={manualId} disabled={busy}
        onChange={(e) => setManualId(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Enter') { void switchTo(Number(manualId)) } }}
        title="가시성 밖 인원으로 전환 — personId 직접 입력"
        style={{ width: 66, textAlign: 'right' }} />
    </div>
  )
}
