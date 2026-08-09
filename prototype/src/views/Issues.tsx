// 유지보수 이슈 — 상태별 뷰 · 담당자/고객사 컬럼 상시 노출 · 미배정/내 담당 필터 · append-only 코멘트 (US-D3)
import { useState } from 'react'
import { addComment, createIssue, updateIssue, useApp } from '../core/store'
import { Empty, ErrorBox, Field, InfoBox, IssueBadge, Modal } from '../components/ui'
import type { IssueStatus, IssueType, MaintenanceIssue } from '../types'

const STATUS_TABS: (IssueStatus | '전체')[] = ['전체', '접수', '처리중', '고객확인대기', '완료']

export default function Issues() {
  const s = useApp()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const [tab, setTab] = useState<IssueStatus | '전체'>('전체')
  const [filter, setFilter] = useState<'' | 'unassigned' | 'mine'>('')
  const [showNew, setShowNew] = useState(false)
  const [openIssue, setOpenIssue] = useState<number | null>(null)

  const rows = s.issues.filter((i) =>
    (tab === '전체' || i.status === tab)
    && (filter !== 'unassigned' || i.assigneeId === null)
    && (filter !== 'mine' || i.assigneeId === me.id),
  )

  return (
    <>
      <h1 className="page">유지보수 이슈</h1>
      <p className="page-desc">구 게시판 대체 — 등록·처리·조회 전사. 담당자 기본값은 사이트 담당 엔지니어입니다(D3-1).</p>
      <div className="tabs">
        {STATUS_TABS.map((t) => (
          <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>
            {t} {t !== '전체' && `(${s.issues.filter((i) => i.status === t).length})`}
          </button>
        ))}
      </div>
      <div className="toolbar">
        <label style={{ fontSize: 13 }}>
          <input type="radio" checked={filter === ''} onChange={() => setFilter('')} /> 전체
        </label>
        <label style={{ fontSize: 13 }}>
          <input type="radio" checked={filter === 'unassigned'} onChange={() => setFilter('unassigned')} /> 미배정만
        </label>
        <label style={{ fontSize: 13 }}>
          <input type="radio" checked={filter === 'mine'} onChange={() => setFilter('mine')} /> 내 담당만
        </label>
        <span style={{ flex: 1 }} />
        <button className="btn primary" onClick={() => setShowNew(true)}>+ 이슈 등록</button>
      </div>
      <div className="card">
        {rows.length === 0 ? <Empty>조건에 맞는 이슈가 없습니다.</Empty> : (
          <table>
            <thead>
              <tr><th>유형</th><th>제목</th><th>고객사</th><th>담당자</th><th>상태</th><th>접수일</th><th>완료일</th></tr>
            </thead>
            <tbody>
              {rows.map((i) => {
                const site = s.sites.find((x) => x.id === i.siteId)
                const assignee = i.assigneeId ? s.people.find((p) => p.id === i.assigneeId) : null
                return (
                  <tr key={i.id} className="clickable" onClick={() => setOpenIssue(i.id)}>
                    <td><span className={`badge ${i.type === '장애' ? 'red' : 'gray'}`}>{i.type}</span></td>
                    <td><b>{i.title}</b></td>
                    <td>{site?.customer}</td>
                    <td>{assignee ? assignee.name : <span className="badge red">미배정</span>}</td>
                    <td><IssueBadge status={i.status} /></td>
                    <td style={{ color: 'var(--muted)', fontSize: 12 }}>{i.receivedAt}</td>
                    <td style={{ color: 'var(--muted)', fontSize: 12 }}>{i.completedAt ?? '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
      {showNew && <NewIssueModal onClose={() => setShowNew(false)} />}
      {openIssue !== null && <IssueDetailModal issueId={openIssue} onClose={() => setOpenIssue(null)} />}
    </>
  )
}

function NewIssueModal({ onClose }: { onClose: () => void }) {
  const s = useApp()
  const [siteId, setSiteId] = useState(0)
  const [type, setType] = useState<IssueType>('문의')
  const [title, setTitle] = useState('')
  const [msg, setMsg] = useState('')
  const engineer = s.people.find((p) => p.id === s.sites.find((x) => x.id === siteId)?.engineerId)

  const submit = () => {
    const r = createIssue(siteId, type, title)
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    onClose()
  }
  return (
    <Modal title="이슈 등록 (전사 누구나)" onClose={onClose}>
      <Field label="사이트 *">
        <select value={siteId} onChange={(e) => setSiteId(Number(e.target.value))}>
          <option value={0}>선택…</option>
          {s.sites.map((site) => {
            const c = s.contracts.find((x) => x.id === site.contractId)
            return <option key={site.id} value={site.id}>{site.customer} — {c?.name}</option>
          })}
        </select>
      </Field>
      {engineer && <InfoBox>담당자가 <b>{engineer.name}</b>(사이트 담당 엔지니어)으로 자동 배정되고 알림이 발송됩니다.</InfoBox>}
      <Field label="유형">
        <select value={type} onChange={(e) => setType(e.target.value as IssueType)}>
          <option value="장애">장애</option><option value="문의">문의</option><option value="요청">요청</option>
        </select>
      </Field>
      <Field label="제목 *"><input value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <div className="actions">
        <button className="btn" onClick={onClose}>취소</button>
        <button className="btn primary" onClick={submit}>등록</button>
      </div>
    </Modal>
  )
}

function IssueDetailModal({ issueId, onClose }: { issueId: number; onClose: () => void }) {
  const s = useApp()
  const issue = s.issues.find((x) => x.id === issueId)
  const [comment, setComment] = useState('')
  const [msg, setMsg] = useState('')
  if (!issue) return null
  const site = s.sites.find((x) => x.id === issue.siteId)
  const comments = s.comments.filter((c) => c.issueId === issueId)
  const nextStatuses: Record<IssueStatus, IssueStatus[]> = {
    접수: ['처리중'], 처리중: ['고객확인대기', '완료'], 고객확인대기: ['완료', '처리중'], 완료: ['처리중'],
  }
  const run = (patch: Parameters<typeof updateIssue>[1]) => {
    const r = updateIssue(issueId, patch)
    setMsg(r.ok ? '' : `${(r as any).code}: ${(r as any).message}`)
  }
  return (
    <Modal title={`[${site?.customer}] ${issue.title}`} onClose={onClose}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12, flexWrap: 'wrap' }}>
        <span className={`badge ${issue.type === '장애' ? 'red' : 'gray'}`}>{issue.type}</span>
        <IssueBadge status={issue.status} />
        <span style={{ fontSize: 12.5, color: 'var(--muted)' }}>접수 {issue.receivedAt}{issue.completedAt && ` · 완료 ${issue.completedAt}`}</span>
      </div>
      <div style={{ display: 'flex', gap: 8, marginBottom: 12, alignItems: 'center' }}>
        <span style={{ fontSize: 13 }}>담당:</span>
        <select
          value={issue.assigneeId ?? 0}
          onChange={(e) => run({ assigneeId: Number(e.target.value) || null })}
        >
          <option value={0}>미배정</option>
          {s.people.filter((p) => p.active && !p.isSystem).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
        {nextStatuses[issue.status].map((st) => (
          <button key={st} className="btn sm" onClick={() => run({ status: st })}>
            {st === '완료' ? '완료 처리' : st === '처리중' && issue.status === '완료' ? '재개 (완료→처리중)' : `→ ${st}`}
          </button>
        ))}
      </div>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <h3 style={{ margin: '14px 0 8px' }}>처리 코멘트 — append-only (수정·삭제 없음, 보정은 새 코멘트로)</h3>
      {comments.length === 0 ? <Empty>코멘트가 없습니다.</Empty> : comments.map((c) => (
        <div key={c.id} style={{ padding: '8px 0', borderBottom: '1px solid #f0f1f4', fontSize: 13 }}>
          <b>{s.people.find((p) => p.id === c.authorId)?.name}</b>
          <span style={{ color: 'var(--muted)', fontSize: 11.5, marginLeft: 8 }}>{c.createdAt}</span>
          <div style={{ marginTop: 3 }}>{c.content}</div>
        </div>
      ))}
      <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
        <input style={{ flex: 1 }} placeholder="코멘트 입력…" value={comment} onChange={(e) => setComment(e.target.value)} />
        <button className="btn primary sm" onClick={() => { const r = addComment(issueId, comment); if (r.ok) setComment('') }}>등록</button>
      </div>
    </Modal>
  )
}
