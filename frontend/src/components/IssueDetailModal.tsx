/*
 * 이슈 처리 (AC D3-2) + 코멘트 (AC D3-3) — 로그인 사용자 전체.
 *
 * **상태 전이 버튼만 그린다**: 갈 수 있는 곳을 `ISSUE_NEXT_STATUSES`로 계산해서
 * 그 상태만 버튼으로 낸다. 전체 상태를 select로 주면 사용자가 흐름 밖 전이를
 * 고르고 서버 409를 받는데, 그 오류는 사용자가 고칠 수 있는 것이 아니다.
 * 정본은 서버(`IssueStatus.canTransitionTo`)이므로 여기서 허용한 전이가 거절될 수도
 * 있고, 그때는 서버 문구를 그대로 보여 준다 — 막는 쪽을 두 벌 두지 않는다.
 *
 * **정정·삭제는 관문이 다르다**(D3-5·D3-6 — 2026-08-26): 상태 전이·코멘트 추가는
 * 전원이지만, 제목·유형·본문 정정과 이슈 삭제는 **등록자·담당자·"계약 관리" 플래그**만
 * 할 수 있다. 화면은 그 판정을 **다시 만들지 않고 서버 응답에서 유도한다**
 * (`reporterId`·`assignee`·`me.manageContracts`) — 판정을 두 벌 두면 A8에서 배운 대로
 * 화면이 "할 수 있다"고 그린 칸에서 서버가 403을 낸다. 실제 방어선은 언제나 서버다.
 *
 * **코멘트 수정·삭제는 작성자 본인만이다**(D3-7). append-only 불변식은 폐기됐지만
 * 범위가 작성자로 좁혀졌고, 고쳐진 코멘트는 "수정됨"으로 표시된다 — 흔적을 지우면
 * 이력이 "처음부터 이렇게 적혀 있었다"고 말하게 된다.
 *
 * 낙관적 락: 이슈를 바꿀 때만 `version`을 보낸다. 한 번 바꾸면 서버가 준 새 이슈로
 * 화면을 갈아 끼운다 — 그러지 않으면 두 번째 변경이 옛 version으로 나가 409가 된다
 * (계약 화면이 같은 이유로 상세를 다시 읽는다). 코멘트는 이슈를 바꾸지 않으므로
 * version이 오르지 않는다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import {
  ISSUE_NEXT_STATUSES, ISSUE_STATUS_LABEL, ISSUE_TYPE_LABEL, ISSUE_TYPE_OF,
  auditTime, shortDate,
} from '../labels'
import { Empty, ErrorText, Field, Modal, ModalActions } from './ui'
import type { IssueStatus, IssueType, IssueView } from '../types/api'

export default function IssueDetailModal({ issue, onClose, onChanged }: {
  issue: IssueView
  onClose: () => void
  onChanged: () => void
}) {
  const {
    me, people, processIssue, addIssueComment, deleteIssue,
    editIssueComment, deleteIssueComment, showToast,
  } = useStore()
  // 서버가 준 최신 이슈로 갈아 끼운다 — version과 코멘트가 여기서 갱신된다
  const [current, setCurrent] = useState(issue)
  const [assigneeId, setAssigneeId] = useState('')
  const [comment, setComment] = useState('')
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<{ type: IssueType; title: string; content: string }>({
    type: ISSUE_TYPE_OF[issue.type] ?? 'INCIDENT',
    title: issue.title,
    content: issue.content ?? '',
  })
  const [editingComment, setEditingComment] = useState<number | null>(null)
  const [commentDraft, setCommentDraft] = useState('')

  /**
   * 정정·삭제 권한 — 서버 판정(`IssueWriteGuard`)과 같은 셋을 응답에서 유도한다.
   * 화면이 이 값을 틀리게 계산해도 서버가 403으로 막는다(방어선은 하나다).
   */
  const canCorrect = me !== null && (
    current.reporterId === me.id
    || current.assignee?.id === me.id
    || me.manageContracts === true)

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

  /** 정정 저장 (D3-5) — 세 칸을 한 요청으로 보낸다. 안 바뀐 칸은 null로 빼서 "그대로"다. */
  const submitCorrection = async () => {
    setBusy(true)
    setError(null)
    const result = await processIssue(current.id, {
      type: form.type === ISSUE_TYPE_OF[current.type] ? null : form.type,
      title: form.title.trim() === current.title ? null : form.title.trim(),
      // 빈 문자열은 "본문을 지운다"다 — 원래도 비어 있었으면 보내지 않는다
      content: form.content === (current.content ?? '') ? null : form.content,
      version: current.version,
    })
    setBusy(false)

    if (!result.ok) {
      setError(result.error)

      return
    }

    setCurrent(result.value)
    setEditing(false)
    showToast('이슈를 수정했습니다')
    onChanged()
  }

  const removeIssue = async () => {
    setBusy(true)
    setError(null)
    const result = await deleteIssue(current.id, current.version)
    setBusy(false)

    if (!result.ok) {
      setError(result.error)

      return
    }

    showToast('이슈를 삭제했습니다')
    onChanged()
    onClose()
  }

  const submitCommentEdit = async (commentId: number) => {
    setBusy(true)
    setError(null)
    const result = await editIssueComment(commentId, { content: commentDraft.trim() })
    setBusy(false)

    if (!result.ok) {
      setError(result.error)

      return
    }

    setCurrent({
      ...current,
      comments: current.comments.map((entry) => (entry.id === commentId ? result.value : entry)),
    })
    setEditingComment(null)
    onChanged()
  }

  const removeComment = async (commentId: number) => {
    setBusy(true)
    setError(null)
    const result = await deleteIssueComment(commentId)
    setBusy(false)

    if (!result.ok) {
      setError(result.error)

      return
    }

    setCurrent({
      ...current,
      comments: current.comments.filter((entry) => entry.id !== commentId),
    })
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

      {/* 정정·삭제는 권한이 있을 때만 그린다 — 없으면 서버가 403이고, 누를 수 없는
          버튼을 보여 주는 것은 화면이 거짓을 말하는 것이다 */}
      {canCorrect && !editing && (
        <div style={{ display: 'flex', gap: 6, marginBottom: 14 }}>
          <button className="btn btn-sm" disabled={busy} onClick={() => setEditing(true)}>
            내용 수정
          </button>
          <button className="btn btn-sm btn-danger-ghost" disabled={busy}
            onClick={() => void removeIssue()}>
            이슈 삭제
          </button>
        </div>
      )}

      {editing && (
        <div style={{ border: '1px solid var(--border-soft)', borderRadius: 6, padding: 12,
          marginBottom: 14 }}>
          <Field label="유형">
            <select value={form.type} disabled={busy}
              onChange={(e) => setForm({ ...form, type: e.target.value as IssueType })}>
              {(Object.keys(ISSUE_TYPE_LABEL) as IssueType[]).map((value) => (
                <option key={value} value={value}>{ISSUE_TYPE_LABEL[value]}</option>
              ))}
            </select>
          </Field>
          <Field label="제목" hint="비울 수 없습니다">
            <input value={form.title} disabled={busy} style={{ width: '100%' }}
              onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </Field>
          <Field label="본문" hint="비우면 본문이 지워집니다">
            <textarea value={form.content} rows={4} disabled={busy} style={{ width: '100%' }}
              onChange={(e) => setForm({ ...form, content: e.target.value })} />
          </Field>
          <ModalActions>
            <button className="btn btn-sm" disabled={busy} onClick={() => {
              setEditing(false)
              setForm({
                type: ISSUE_TYPE_OF[current.type] ?? 'INCIDENT',
                title: current.title,
                content: current.content ?? '',
              })
            }}>취소</button>
            <button className="btn btn-primary btn-sm"
              disabled={busy || form.title.trim() === ''}
              onClick={() => void submitCorrection()}>
              {busy ? '저장 중…' : '저장'}
            </button>
          </ModalActions>
        </div>
      )}

      {!editing && current.content !== null && (
        <div style={{ background: 'var(--chip)', borderRadius: 6, padding: '10px 12px',
          marginBottom: 14, fontSize: 13, whiteSpace: 'pre-wrap' }}>
          {current.content}
        </div>
      )}

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
            {' '}· 자기가 남긴 코멘트만 수정·삭제할 수 있습니다
          </span>
        </div>

        {current.comments.length === 0 && <Empty>코멘트가 없습니다.</Empty>}

        <div style={{ display: 'grid', gap: 8 }}>
          {current.comments.map((entry) => (
            <div key={entry.id} style={{ background: 'var(--chip)', borderRadius: 6,
              padding: '8px 10px' }}>
              <div className="muted2" style={{ fontSize: 11.5, marginBottom: 3,
                display: 'flex', gap: 6, alignItems: 'center' }}>
                <span>{entry.author?.name ?? '알 수 없음'} · {auditTime(entry.createdAt)}</span>
                {/* 고쳐진 코멘트는 그렇다고 말한다 — 흔적을 지우면 이력이 거짓이 된다 */}
                {entry.updatedAt !== null && <span className="badge" style={{ fontSize: 10 }}>수정됨</span>}
                {/* 작성자 본인에게만 그린다 (D3-7). 방어선은 서버의 403이다 */}
                {me !== null && entry.author?.id === me.id && editingComment !== entry.id && (
                  <span style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
                    <button className="btn btn-sm" disabled={busy} onClick={() => {
                      setEditingComment(entry.id)
                      setCommentDraft(entry.content)
                    }}>수정</button>
                    <button className="btn btn-sm btn-danger-ghost" disabled={busy}
                      onClick={() => void removeComment(entry.id)}>삭제</button>
                  </span>
                )}
              </div>
              {editingComment === entry.id ? (
                <>
                  <textarea value={commentDraft} rows={3} disabled={busy}
                    style={{ width: '100%' }}
                    onChange={(e) => setCommentDraft(e.target.value)} />
                  <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                    <button className="btn btn-sm" disabled={busy}
                      onClick={() => setEditingComment(null)}>취소</button>
                    <button className="btn btn-primary btn-sm"
                      disabled={busy || commentDraft.trim() === ''}
                      onClick={() => void submitCommentEdit(entry.id)}>저장</button>
                  </div>
                </>
              ) : (
                <div style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{entry.content}</div>
              )}
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
