/*
 * 이슈 처리 (AC D3-2) + 코멘트 (AC D3-3) — 로그인 사용자 전체.
 *
 * **상태 전이 버튼만 그린다**: 갈 수 있는 곳을 `ISSUE_NEXT_STATUSES`로 계산해서
 * 그 상태만 버튼으로 낸다. 전체 상태를 select로 주면 사용자가 흐름 밖 전이를
 * 고르고 서버 409를 받는데, 그 오류는 사용자가 고칠 수 있는 것이 아니다.
 * 정본은 서버(`IssueStatus.canTransitionTo`)이므로 여기서 허용한 전이가 거절될 수도
 * 있고, 그때는 서버 문구를 그대로 보여 준다 — 막는 쪽을 두 벌 두지 않는다.
 *
 * **코멘트는 append-only다**(D3-3): 수정·삭제 버튼이 없는 것이 그 규칙이다.
 * 보정은 새 코멘트로만 하고, 그래서 이슈의 `version`도 올라가지 않는다.
 *
 * 낙관적 락: 상태·담당을 바꿀 때만 `version`을 보낸다. 한 번 바꾸면 서버가 준 새
 * 이슈로 화면을 갈아 끼운다 — 그러지 않으면 두 번째 변경이 옛 version으로 나가
 * 409가 된다(계약 화면이 같은 이유로 상세를 다시 읽는다).
 */
import { useState } from 'react'
import { useStore } from '../store'
import { ISSUE_NEXT_STATUSES, ISSUE_STATUS_LABEL, auditTime, shortDate } from '../labels'
import { Empty, ErrorText, Field, Modal, ModalActions } from './ui'
import type { IssueStatus, IssueView } from '../types/api'

export default function IssueDetailModal({ issue, onClose, onChanged }: {
  issue: IssueView
  onClose: () => void
  onChanged: () => void
}) {
  const { people, processIssue, addIssueComment, showToast } = useStore()
  // 서버가 준 최신 이슈로 갈아 끼운다 — version과 코멘트가 여기서 갱신된다
  const [current, setCurrent] = useState(issue)
  const [assigneeId, setAssigneeId] = useState('')
  const [comment, setComment] = useState('')
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)

  const apply = async (patch: { status?: IssueStatus; assigneeId?: number }) => {
    setBusy(true)
    setError(null)
    const result = await processIssue(current.id, { ...patch, version: current.version })
    setBusy(false)

    if (!result.ok) {
      setError(result.error)

      return
    }

    setCurrent(result.value)
    setAssigneeId('')
    showToast(patch.status !== undefined
      ? `상태를 ${ISSUE_STATUS_LABEL[patch.status]}(으)로 바꿨습니다`
      : '담당자를 바꿨습니다')
    onChanged()
  }

  const submitComment = async () => {
    setBusy(true)
    setError(null)
    const result = await addIssueComment(current.id, { content: comment.trim() })
    setBusy(false)

    if (!result.ok) {
      setError(result.error)

      return
    }

    // 코멘트는 이슈를 바꾸지 않으므로 version은 그대로다 — 목록에만 덧붙인다
    setCurrent({ ...current, comments: [...current.comments, result.value] })
    setComment('')
    onChanged()
  }

  return (
    <Modal title={current.title} width={620} onClose={onClose}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap',
        marginBottom: 14 }}>
        <span className="badge" style={{ fontSize: 11.5 }}>{current.type}</span>
        <span className="badge" style={{ fontSize: 11.5 }}>{current.status}</span>
        <span className="muted2" style={{ fontSize: 12 }}>
          {current.siteName ?? '사이트 미연결'}
          {current.contractName && ` · ${current.contractName}`}
        </span>
        <span className="muted2" style={{ fontSize: 12, marginLeft: 'auto' }}>
          접수 {shortDate(current.receivedAt)}
          {current.completedAt && ` · 완료 ${shortDate(current.completedAt)}`}
        </span>
      </div>

      <Field label="상태" hint="흐름에서 갈 수 있는 곳만 나옵니다">
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {ISSUE_NEXT_STATUSES[current.statusCode].map((next) => (
            <button key={next} className="btn btn-sm" disabled={busy}
              onClick={() => void apply({ status: next })}>
              {/* 완료에서 처리중으로 가는 것은 되돌리기라 이름을 달리 부른다 */}
              {current.statusCode === 'DONE' && next === 'IN_PROGRESS'
                ? '재개 (처리중으로)'
                : `${ISSUE_STATUS_LABEL[next]}로`}
            </button>
          ))}
        </div>
      </Field>

      <Field label="담당자" hint={current.assignee ? `현재 ${current.assignee.name}` : '현재 미배정'}>
        <div style={{ display: 'flex', gap: 6 }}>
          <select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
            <option value="">— 재배정할 사람 선택 —</option>
            {people.map((person) => (
              <option key={person.id} value={person.id}>{person.name}</option>
            ))}
          </select>
          <button className="btn btn-sm" disabled={busy || assigneeId === ''}
            onClick={() => void apply({ assigneeId: Number(assigneeId) })}>
            재배정
          </button>
        </div>
        {/* 해제 경로가 없는 것은 의도다 — AC에 요구가 없고 빈 값은 "그대로"를 뜻한다 */}
      </Field>

      {error && <ErrorText code={error.code} message={error.message} />}

      <div style={{ marginTop: 18, borderTop: '1px solid var(--border-soft)', paddingTop: 14 }}>
        <div className="muted" style={{ fontWeight: 600, fontSize: 12.5, marginBottom: 8 }}>
          코멘트 {current.comments.length}건
          <span className="muted2" style={{ fontWeight: 500 }}>
            {' '}· 남긴 코멘트는 수정·삭제할 수 없습니다
          </span>
        </div>

        {current.comments.length === 0 && <Empty>코멘트가 없습니다.</Empty>}

        <div style={{ display: 'grid', gap: 8 }}>
          {current.comments.map((entry) => (
            <div key={entry.id} style={{ background: 'var(--chip)', borderRadius: 6,
              padding: '8px 10px' }}>
              <div className="muted2" style={{ fontSize: 11.5, marginBottom: 3 }}>
                {entry.author?.name ?? '알 수 없음'} · {auditTime(entry.createdAt)}
              </div>
              <div style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{entry.content}</div>
            </div>
          ))}
        </div>

        <textarea value={comment} onChange={(e) => setComment(e.target.value)} rows={3}
          placeholder="처리 내용을 남깁니다" style={{ marginTop: 10, width: '100%' }} />

        <ModalActions>
          <button className="btn btn-primary btn-sm" disabled={busy || comment.trim() === ''}
            onClick={() => void submitComment()}>
            {busy ? '저장 중…' : '코멘트 추가'}
          </button>
        </ModalActions>
      </div>
    </Modal>
  )
}
