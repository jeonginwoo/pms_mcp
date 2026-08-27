/*
 * AI 어시스턴트 대화 상태 — 패널이 닫혀도 살아 있어야 해서 Shell에 산다(theme.ts와 같은 꼴).
 *
 * 대화 기억은 서버가 들고 있다: host가 `sub + ":" + conversationId`로 최근 10턴을 보관하므로
 * (ChatService), 화면은 id를 한 번 만들어 고정하고 '새 대화'가 그것을 갈아 끼운다 —
 * 말풍선만 지우면 화면은 비었는데 서버는 앞 대화를 기억하는 상태가 된다.
 * 새로고침하면 말풍선도 id도 사라진다(기억을 브라우저에 남기지 않는다).
 */
import { useCallback, useRef, useState } from 'react'
import { ApiError, chat as ask } from './api'

export interface ChatMessage {
  role: 'user' | 'ai'
  text: string
  /** 실패한 답 — 말풍선을 오류로 칠한다. 다시 물어보면 그만이라 재시도 버튼은 두지 않는다 */
  failed?: boolean
}

export interface Chat {
  messages: ChatMessage[]
  pending: boolean
  send: (text: string) => Promise<void>
  /** 기다리기를 그만둔다 (FR-AI-03) — 아래 ABORTED가 그 한계를 적는다 */
  abort: () => void
  reset: () => void
}

/**
 * 중단은 **기다리기를 그만두는 것이지 되돌리기가 아니다.** 요청은 이미 서버에 가 있고
 * 에이전트 루프는 계속 돈다 — 확정까지 마친 쓰기였다면 그것은 그대로 커밋된다.
 * 화면이 그 사실을 말하지 않으면 사용자는 취소된 줄 안다.
 */
const ABORTED = '기다리기를 중단했습니다. 서버에서 이미 시작된 작업은 취소되지 않습니다 '
  + '— 결과는 화면을 새로고침해 확인해 주세요.'

export function useChat(): Chat {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [pending, setPending] = useState(false)
  const [conversationId, setConversationId] = useState(() => crypto.randomUUID())
  const inflight = useRef<AbortController | null>(null)

  const send = useCallback(async (text: string) => {
    // 한 번에 한 질문만 보낸다 — 답을 기다리는 중에 또 보내면 서버 기억에 순서가 뒤엉켜 쌓인다
    if (pending) {
      return
    }

    const controller = new AbortController()

    inflight.current = controller
    setMessages((prev) => [...prev, { role: 'user', text }])
    setPending(true)

    try {
      const { reply } = await ask(conversationId, text, controller.signal)

      setMessages((prev) => [...prev, { role: 'ai', text: reply }])
    } catch (e) {
      const text = controller.signal.aborted ? ABORTED : reasonOf(e)

      setMessages((prev) => [...prev, { role: 'ai', text, failed: true }])
    } finally {
      inflight.current = null
      setPending(false)
    }
  }, [conversationId, pending])

  const abort = useCallback(() => {
    inflight.current?.abort()
  }, [])

  const reset = useCallback(() => {
    setMessages([])
    setConversationId(crypto.randomUUID())
  }, [])

  return { messages, pending, send, abort, reset }
}

/**
 * 실패 사유. 대역 엔드포인트는 §7 봉투를 쓰지 않고 Boot 기본 오류 본문을 주는데 거기엔
 * message가 없다(`server.error.include-message` 기본값 never). 그래서 문구는 api.ts가
 * 상태 코드로 가려 붙이고, 여기서는 연결 자체가 안 된 경우만 따로 답한다.
 */
function reasonOf(e: unknown): string {
  return e instanceof ApiError
    ? e.message
    : '어시스턴트에 연결하지 못했습니다 — host 앱(8081)이 떠 있는지 확인하세요.'
}
