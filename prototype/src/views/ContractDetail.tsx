// 계약 상세 — 계약 정보(원 프로젝트 링크) · 사이트(담당 엔지니어 정본) · 연락처 · 이슈 이력 (D4-2)
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { saveSite, useApp } from '../core/store'
import { orgCanManageContract } from '../core/permissions'
import { ContractBadge, Empty, ErrorBox, Field, IssueBadge, Modal } from '../components/ui'
import { ContractModal } from './Maintenance'
import type { MaintenanceSite } from '../types'

export default function ContractDetail() {
  const { id } = useParams()
  const s = useApp()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const c = s.contracts.find((x) => x.id === Number(id))
  const [showEdit, setShowEdit] = useState(false)
  const [editSite, setEditSite] = useState<MaintenanceSite | 'new' | null>(null)

  if (!c) return <Empty>계약을 찾을 수 없습니다.</Empty>
  const sites = s.sites.filter((x) => x.contractId === c.id)
  const siteIds = sites.map((x) => x.id)
  const issues = s.issues.filter((i) => siteIds.includes(i.siteId))
  const contacts = s.contacts.filter((x) => siteIds.includes(x.siteId))
  const sourceProject = c.sourceProjectId ? s.projects.find((p) => p.id === c.sourceProjectId) : null
  const canManage = orgCanManageContract(me, s.roleGroups)

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
        <h1 className="page">{c.name}</h1>
        <ContractBadge status={c.status} />
      </div>
      <p className="page-desc">
        {c.counterparty} · {c.startDate} ~ {c.endDate} · 연 {c.amount.toLocaleString()}원 (월 {c.monthlyAmount.toLocaleString()}원)
        {c.salesRepId && ` · 영업대표 ${s.people.find((p) => p.id === c.salesRepId)?.name}`}
        {' · '}
        {sourceProject
          ? <>원 프로젝트: <Link to={`/projects/${sourceProject.id}`}>{sourceProject.name}</Link></>
          : '직접 등록 (원천 프로젝트 없음)'}
      </p>
      {c.inspectionNote && <div className="info-box">정기점검: {c.inspectionNote}</div>}
      {c.note && <p style={{ color: 'var(--muted)', fontSize: 13 }}>비고: {c.note}</p>}
      {canManage && <div className="toolbar"><button className="btn" onClick={() => setShowEdit(true)}>계약 수정</button></div>}

      <div className="card">
        <h3>사이트 ({sites.length}) — 담당 엔지니어의 정본은 사이트 단위입니다(이슈 기본 배정 원천)</h3>
        {sites.length === 0 ? <Empty>사이트가 없습니다.</Empty> : (
          <table>
            <thead><tr><th>고객사</th><th>솔루션/버전</th><th>대상</th><th>서버 스펙</th><th>담당 엔지니어</th><th>열린 이슈</th>{canManage && <th></th>}</tr></thead>
            <tbody>
              {sites.map((site) => {
                const eng = s.people.find((p) => p.id === site.engineerId)
                const open = s.issues.filter((i) => i.siteId === site.id && i.status !== '완료').length
                return (
                  <tr key={site.id}>
                    <td><b>{site.customer}</b></td>
                    <td>{site.solution}</td>
                    <td><span className="badge gray">{site.target}</span></td>
                    <td style={{ fontSize: 12, color: 'var(--muted)' }}>{site.serverSpec}</td>
                    <td><span className="badge blue">{eng?.name ?? '미지정'}</span></td>
                    <td>{open > 0 ? <span className="badge red">{open}</span> : '—'}</td>
                    {canManage && <td><button className="btn sm" onClick={() => setEditSite(site)}>수정</button></td>}
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
        {canManage && <button className="btn sm" style={{ marginTop: 10 }} onClick={() => setEditSite('new')}>+ 사이트 추가</button>}
      </div>

      <div className="grid cols-2">
        <div className="card">
          <h3>담당자 연락처</h3>
          {contacts.length === 0 ? <Empty>등록된 연락처가 없습니다.</Empty> : (
            <table>
              <thead><tr><th>구분</th><th>사이트</th><th>이름</th><th>직급</th><th>연락처</th></tr></thead>
              <tbody>
                {contacts.map((ct) => (
                  <tr key={ct.id}>
                    <td><span className="badge gray">{ct.kind}</span></td>
                    <td>{sites.find((x) => x.id === ct.siteId)?.customer}</td>
                    <td>{ct.name}</td>
                    <td>{ct.title}</td>
                    <td style={{ fontSize: 12 }}>{ct.phone}<br />{ct.email}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div className="card">
          <h3>이슈 이력 ({issues.length}) — <Link to="/maintenance/issues">이슈 목록에서 처리</Link></h3>
          {issues.length === 0 ? <Empty>이슈가 없습니다.</Empty> : (
            <table>
              <thead><tr><th>사이트</th><th>유형</th><th>제목</th><th>상태</th><th>담당</th></tr></thead>
              <tbody>
                {issues.map((i) => (
                  <tr key={i.id}>
                    <td>{sites.find((x) => x.id === i.siteId)?.customer}</td>
                    <td><span className="badge gray">{i.type}</span></td>
                    <td>{i.title}</td>
                    <td><IssueBadge status={i.status} /></td>
                    <td>{i.assigneeId ? s.people.find((p) => p.id === i.assigneeId)?.name : <span className="badge red">미배정</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
      {showEdit && <ContractModal contract={c} onClose={() => setShowEdit(false)} />}
      {editSite && <SiteModal contractId={c.id} site={editSite === 'new' ? undefined : editSite} onClose={() => setEditSite(null)} />}
    </>
  )
}

function SiteModal({ contractId, site, onClose }: { contractId: number; site?: MaintenanceSite; onClose: () => void }) {
  const s = useApp()
  const [form, setForm] = useState({
    customer: site?.customer ?? '', solution: site?.solution ?? '',
    target: site?.target ?? ('솔루션' as const), serverSpec: site?.serverSpec ?? '',
    engineerId: site?.engineerId ?? 0,
  })
  const [msg, setMsg] = useState('')
  const submit = () => {
    if (!form.engineerId) { setMsg('담당 엔지니어는 필수입니다.'); return }
    const r = saveSite({ ...form, contractId, id: site?.id })
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    onClose()
  }
  return (
    <Modal title={site ? '사이트 수정' : '사이트 추가'} onClose={onClose}>
      <Field label="고객사명 *"><input value={form.customer} onChange={(e) => setForm({ ...form, customer: e.target.value })} /></Field>
      <Field label="솔루션/버전"><input value={form.solution} onChange={(e) => setForm({ ...form, solution: e.target.value })} /></Field>
      <Field label="대상">
        <select value={form.target} onChange={(e) => setForm({ ...form, target: e.target.value as '인프라' | '솔루션' })}>
          <option value="솔루션">솔루션</option><option value="인프라">인프라</option>
        </select>
      </Field>
      <Field label="서버 스펙"><input value={form.serverSpec} onChange={(e) => setForm({ ...form, serverSpec: e.target.value })} /></Field>
      <Field label="담당 엔지니어 * (이슈 기본 배정 원천)">
        <select value={form.engineerId} onChange={(e) => setForm({ ...form, engineerId: Number(e.target.value) })}>
          <option value={0}>선택…</option>
          {s.people.filter((p) => p.active && !p.isSystem).map((p) => <option key={p.id} value={p.id}>{p.name} — {p.team}</option>)}
        </select>
      </Field>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <div className="actions">
        <button className="btn" onClick={onClose}>취소</button>
        <button className="btn primary" onClick={submit}>저장</button>
      </div>
    </Modal>
  )
}
