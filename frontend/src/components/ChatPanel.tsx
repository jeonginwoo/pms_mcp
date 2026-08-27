/*
 * AI 어시스턴트 패널 (FR-AI-01) — 질문을 보내고 답을 보여 준다. 그뿐이다.
 *
 * **확인 카드(FR-AI-04)가 없다 — 미이행이고, 계약 대기다.** react-ts 규약 §4는 쓰기에
 * 확인 카드([실행]/[취소])를 요구한다. 그런데 카드를 그리려면 응답이 "지금 쓰기 확인 중이고
 * 요약은 이것"이라고 **말해 줘야** 하는데, 대역이 주는 것은 `{conversationId, reply}` 즉
 * 글자뿐이라 화면이 알 방법이 없다. 그동안 쓰기를 지키는 것은 원칙 5의 2단계 확인이다 —
 * 요약을 받고 "확정"이라고 답해야 쓰기가 나가고 그 판정은 에이전트와 서버가 한다.
 * 그 신호는 BFF 계약을 정할 때 함께 정한다(공용 결정 기록 2026-08-27).
 *
 * 피드백 버튼(FR-AI-05)도 미이행이다 — 저장할 `/api/chat/feedback`이 아직 없고, 이 앱의
 * 규칙은 **없는 엔드포인트를 동작하는 것처럼 그리지 않는 것**이다(규약 §3).
 *
 * 답변을 마크다운으로 그리는 것과 그 렌더 범위를 좁힌 근거는 `ChatAnswer.tsx`가 적는다.
 */
import { useEffect, useRef, useState } from 'react'
import { useStore } from '../store'
import ChatAnswer from './ChatAnswer'
import type { Chat } from '../chat'

/**
 * 한 번에 보낼 수 있는 최대 글자 수 (FR-AI-02) — **서버에 닿기 전에 막는다.**
 * BFF가 서면 서버에서도 같은 수로 자르지만(구현_노트 §1-2) 그것은 이 규칙의 대체가
 * 아니다: 버튼이 살아 있는데 서버가 거절하면 사용자는 이유를 답으로 받아야 한다.
 */
const MAX_INPUT = 2_000

/** 첫 화면의 예시 질문 — 도구 7종 중 조회 경로(whoami·프로젝트·가동률)를 한 번씩 짚는다 */
const SUGGESTIONS = [
  '나 누구야?',
  '내가 참여 중인 프로젝트 알려줘',
  '이번 달 내 가동률은?',
]

interface Props {
  chat: Chat
  onClose: () => void
}

export default function ChatPanel({ chat, onClose }: Props) {
  const { me, sessionMode } = useStore()
  const [input, setInput] = useState('')
  const bodyRef = useRef<HTMLDivElement>(null)

  // 말풍선이 붙을 때마다 바닥에 붙어 있게 한다 — 기다리는 표시가 켜질 때도 마찬가지다
  useEffect(() => {
    bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight })
  }, [chat.messages, chat.pending])

  const tooLong = input.length > MAX_INPUT

  const submit = () => {
    const text = input.trim()

    if (!text || tooLong || chat.pending) {
      return
    }

    setInput('')
    void chat.send(text)
  }

  return (
    <aside className="chat-panel">
      <div className="chat-head">
        <b>AI 어시스턴트</b>
        <div style={{ display: 'flex', gap: 6 }}>
          <button
            className="btn btn-ghost btn-sm"
            onClick={chat.reset}
            disabled={chat.messages.length === 0 || chat.pending}
          >
            새 대화
          </button>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>닫기</button>
        </div>
      </div>

      <div className="chat-body" ref={bodyRef}>
        {chat.messages.length === 0 && (
          <>
            <div className="bubble ai">
              {`안녕하세요${me ? ` ${me.name}님` : ''}. 프로젝트 · 가동률 · 유지보수를 물어보세요.\n`}
              {'보이는 범위는 화면과 같습니다 — 권한 밖은 있는지 없는지도 답하지 않습니다.'}
            </div>
            {sessionMode === 'token' && SUGGESTIONS.map((s) => (
              <button key={s} className="suggest" onClick={() => void chat.send(s)}>{s}</button>
            ))}
          </>
        )}

        {chat.messages.map((m, i) => (m.role === 'ai' && !m.failed
          ? <ChatAnswer key={i} text={m.text} />
          /* 내가 친 말과 오류 문구는 마크다운이 아니다 — 글자 그대로 둔다 */
          : <div key={i} className={`bubble ${m.role}${m.failed ? ' err' : ''}`}>{m.text}</div>
        ))}

        {/* 진행 상태 (FR-AI-03) — 대역은 동기 응답이라 어느 도구를 도는 중인지는 알 수 없다 */}
        {chat.pending && <div className="bubble ai muted">답을 만들고 있습니다…</div>}
      </div>

      {sessionMode === 'token' ? (
        <>
          {tooLong && (
            <div className="chat-notice">
              한 번에 {MAX_INPUT.toLocaleString()}자까지 보낼 수 있습니다
              (지금 {input.length.toLocaleString()}자). 나눠서 물어봐 주세요.
            </div>
          )}
          <div className="chat-input-row">
            <input
              style={{ flex: 1 }}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && submit()}
              placeholder="예: 이번 달 내 가동률은?"
            />
            {/* 기다리는 동안 같은 자리가 중단 버튼이 된다 (FR-AI-03) */}
            {chat.pending ? (
              <button className="btn btn-ghost" onClick={chat.abort}>중단</button>
            ) : (
              <button className="btn btn-primary" onClick={submit} disabled={tooLong}>전송</button>
            )}
          </div>
        </>
      ) : (
        /*
         * 화자 지정 세션에는 서버로 보낼 토큰이 없다. 이것은 임시 배선의 결함이 아니라
         * 정본과 같은 성질이다 — BFF도 Authentication을 요구한다(구현_노트 §1-2).
         */
        <div className="chat-input-row muted2" style={{ display: 'block', fontSize: 12 }}>
          어시스턴트는 <b>로그인 세션에서만</b> 동작합니다 — 화자 지정으로 들어온 세션에는
          서버에 보낼 토큰이 없습니다.
        </div>
      )}
    </aside>
  )
}
