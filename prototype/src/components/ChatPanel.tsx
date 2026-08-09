// AI 어시스턴트 패널 — 최종 UI 미리보기용 목업 (피드백 #0)
// 실제 에이전트 루프·LLM·MCP는 host 앱 소유(M1 챗 BFF 연동) — 여기는 화면과 대화 흐름만 재현한다.
// 재현 흐름: whoami · 내 프로젝트 · 가동률 · 진척률 수정(확인 카드 → 확정/취소, SC-11·21·22) · 권한 거절 전달
import { useRef, useState } from 'react'
import { CURRENT_MONTH, saveProgress, useApp, getState } from '../core/store'
import { utilizationFor } from '../core/utilization'
import { visibleProjects, orgScopePeople } from '../core/visibility'
import { groupOf } from '../core/permissions'

interface ConfirmCard {
  projectId: number
  projectName: string
  value: number
  version: number
  summary: string
  resolved?: '확정' | '취소'
}
interface Msg { role: 'user' | 'bot'; text: string; card?: ConfirmCard }

const STOPWORDS = ['진척률', '진행률', '진척', '진행', '프로젝트', '수정', '변경', '해줘', '해주세요', '으로', '바꿔줘', '바꿔', '저장', '알려줘', '알려', '가동률', '내']

export default function ChatPanel({ onClose }: { onClose: () => void }) {
  const s = useApp()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const [msgs, setMsgs] = useState<Msg[]>([{
    role: 'bot',
    text: `안녕하세요 ${me.name}님, PMS AI 어시스턴트(목업)입니다.\n이렇게 물어보세요:\n· "나 누구야?"\n· "내 프로젝트 알려줘"\n· "이번 달 내 가동률은?"\n· "근로복지공단 진행률 80%로 수정해줘"`,
  }])
  const [input, setInput] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)

  const push = (...m: Msg[]) => {
    setMsgs((prev) => [...prev, ...m])
    setTimeout(() => scrollRef.current?.scrollTo({ top: 99999 }), 30)
  }

  const send = () => {
    const text = input.trim()
    if (!text) return
    setInput('')
    push({ role: 'user', text }, route(text))
  }

  function route(text: string): Msg {
    // whoami
    if (/누구|내 정보|whoami/i.test(text)) {
      return { role: 'bot', text: `${me.name}님입니다.\n· 소속: ${me.division} · ${me.team}\n· 직급: ${me.grade}\n· 권한 그룹: ${groupOf(me, s.roleGroups).name}\n(프로젝트별 역할은 프로젝트마다 다릅니다 — whoami는 전역 정보만 반환)` }
    }
    // 진척률 수정 — 확인 카드(2단계) 재현
    const pct = text.match(/(\d{1,3})\s*(%|퍼|프로)/) ?? (/진척|진행/.test(text) ? text.match(/(\d{1,3})/) : null)
    if (pct && /진척|진행|수정|변경|바꿔/.test(text)) {
      return progressIntent(text, Number(pct[1]))
    }
    // 내 프로젝트
    if (/내 프로젝트|프로젝트 (뭐|목록|알려)/.test(text)) {
      const mine = s.assignments
        .filter((a) => a.personId === me.id && a.status === 'ACTIVE')
        .map((a) => ({ a, p: s.projects.find((x) => x.id === a.projectId)! }))
        .filter(({ p }) => p && !p.deleted && ['진행중', '수주확정', '계약대기'].includes(p.status))
      if (mine.length === 0) return { role: 'bot', text: '현재 배정된 진행 프로젝트가 없습니다.' }
      return { role: 'bot', text: `배정된 프로젝트 ${mine.length}건:\n` + mine.map(({ a, p }) => `· ${p.name} — ${p.status} ${p.progress}% (역할 ${a.role === 'PARTICIPANT' ? '참여자' : a.role})`).join('\n') }
    }
    // 가동률 (본인 또는 가시성 범위 내 인물)
    if (/가동률/.test(text)) {
      const scoped = orgScopePeople(me, s.people.filter((p) => p.active && !p.isSystem), s)
      const target = scoped.find((p) => p.id !== me.id && text.includes(p.name)) ?? me
      const named = s.people.find((p) => !p.isSystem && p.id !== me.id && text.includes(p.name))
      if (named && !scoped.some((p) => p.id === named.id)) {
        return { role: 'bot', text: '해당 인원을 찾을 수 없습니다. (가시성 범위 밖 — 부재와 구분하지 않는 은닉 응답)' }
      }
      const [u] = utilizationFor(CURRENT_MONTH, [target], s.projects, s.assignments)
      return { role: 'bot', text: `${target.id === me.id ? '' : target.name + '님의 '}${CURRENT_MONTH.slice(5)}월 가동률:\n· 기본 ${Math.round(u.basic)}% (Σ${u.totalMM.toFixed(1)}MM ÷ 가용 1.0)\n· 보정 ${Math.round(u.adjusted)}% (직급계수 ${target.gradeCoeff})${u.adjusted > 100 ? '\n⚠ 과부하 상태입니다.' : ''}` }
    }
    return { role: 'bot', text: '죄송해요, 목업 챗봇은 데모 시나리오만 이해합니다.\n· "나 누구야?" · "내 프로젝트 알려줘" · "이번 달 내 가동률은?" · "〈프로젝트명 일부〉 진행률 80%로 수정해줘"\n(실제 어시스턴트의 이해 범위는 host 앱 Eval로 검증됩니다)' }
  }

  function progressIntent(text: string, value: number): Msg {
    const visible = visibleProjects(me, s.projects, s.assignments, s)
    const tokens = text.split(/[\s,.!?~를을에서의]+/)
      .map((t) => t.replace(/(\d|%|퍼|프로)+/g, ''))
      .filter((t) => t.length >= 2 && !STOPWORDS.includes(t))
    const candidates = visible.filter((p) => tokens.some((t) => p.name.includes(t)))
    if (candidates.length === 0) {
      return { role: 'bot', text: '어느 프로젝트인지 찾지 못했습니다. 프로젝트명을 함께 말씀해 주세요. (가시성 범위 밖 프로젝트는 존재 여부도 안내하지 않습니다)' }
    }
    if (candidates.length > 1) {
      return { role: 'bot', text: `대상이 모호합니다 — ${candidates.length}건이 일치해요. 어느 것인가요?\n` + candidates.slice(0, 5).map((p) => `· ${p.name}`).join('\n') + '\n(쓰기는 대상이 특정될 때까지 실행하지 않습니다)' }
    }
    const p = candidates[0]
    const r = saveProgress(p.id, value, p.version, false) // confirmed=false — 요약만, DB 미변경
    if (!r.ok) {
      return { role: 'bot', text: `진행할 수 없습니다 — ${r.message} (${r.code})\n권한 판정은 서버가 최종 수행하고, 저는 거절을 그대로 전달합니다.` }
    }
    return {
      role: 'bot',
      text: '변경 내용을 확인해 주세요. 확정 전에는 저장되지 않습니다.',
      card: { projectId: p.id, projectName: p.name, value, version: p.version, summary: r.data.preview ?? '' },
    }
  }

  const resolveCard = (idx: number, confirm: boolean) => {
    const card = msgs[idx].card!
    if (!confirm) {
      setMsgs((prev) => prev.map((m, i) => (i === idx ? { ...m, card: { ...card, resolved: '취소' } } : m)))
      push({ role: 'bot', text: '취소했습니다. 아무것도 변경되지 않았습니다.' })
      return
    }
    const r = saveProgress(card.projectId, card.value, card.version, true) // confirmed=true — 커밋
    setMsgs((prev) => prev.map((m, i) => (i === idx ? { ...m, card: { ...card, resolved: '확정' } } : m)))
    if (!r.ok) {
      push({ role: 'bot', text: `저장하지 못했습니다 — ${r.message} (${r.code})${r.code === 'STALE_VERSION' ? '\n최신 내용을 다시 확인한 뒤 재시도해 주세요.' : ''}` })
      return
    }
    const done = getState().projects.find((x) => x.id === card.projectId)!
    push({
      role: 'bot',
      text: `저장했습니다 — [${card.projectName}] 진행률 ${card.value}%.`
        + (r.data.completable ? '\n진행률이 100%지만 상태는 그대로입니다. 검수·납품까지 끝났다면 화면에서 완료 처리를 진행하세요. (완료 처리는 챗에서 실행하지 않습니다)' : '')
        + `\n(version ${done.version} · 감사 로그 기록됨)`,
    })
  }

  return (
    <div className="chat-panel">
      <div className="chat-head">
        <b>AI 어시스턴트</b>
        <span style={{ fontSize: 11, color: 'var(--muted)' }}>목업 — 실제 에이전트는 host 앱(M1 연동)</span>
        <button className="btn sm" onClick={onClose}>닫기</button>
      </div>
      <div className="chat-msgs" ref={scrollRef}>
        {msgs.map((m, i) => (
          <div key={i} className={`msg ${m.role}`}>
            <div className="bubble">{m.text}</div>
            {m.card && (
              <div className="confirm-card">
                <div style={{ fontWeight: 600, marginBottom: 6 }}>쓰기 확인</div>
                <div style={{ fontSize: 13 }}>{m.card.summary}</div>
                {m.card.resolved ? (
                  <div style={{ marginTop: 8 }}><span className={`badge ${m.card.resolved === '확정' ? 'green' : 'gray'}`}>{m.card.resolved}</span></div>
                ) : (
                  <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
                    <button className="btn primary sm" onClick={() => resolveCard(i, true)}>확정</button>
                    <button className="btn sm" onClick={() => resolveCard(i, false)}>취소</button>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
      <div className="chat-input">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && send()}
          placeholder="예: 근로복지공단 진행률 80%로 수정해줘"
        />
        <button className="btn primary sm" onClick={send}>전송</button>
      </div>
    </div>
  )
}
