// 유지보수 계약 목록 — 유지보수 탭의 원천(시트 대체). 전사 조회(D4-3 — 조직 가시성 미적용)
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { saveContract, useApp } from '../core/store'
import { orgCanManageContract } from '../core/permissions'
import { ContractBadge, Empty, ErrorBox, Field, Modal } from '../components/ui'
import type { ContractStatus, MaintenanceContract } from '../types'

export default function Maintenance() {
  const s = useApp()
  const nav = useNavigate()
  const me = s.people.find((p) => p.id === s.currentUserId)!
  const [status, setStatus] = useState<ContractStatus | ''>('')
  const [q, setQ] = useState('')
  const [endBefore, setEndBefore] = useState('')
  const [showNew, setShowNew] = useState(false)

  const rows = s.contracts.filter((c) =>
    (!status || c.status === status)
    && (!q || c.counterparty.includes(q) || c.name.includes(q))
    && (!endBefore || c.endDate <= endBefore),
  )

  return (
    <>
      <h1 className="page">유지보수 계약</h1>
      <p className="page-desc">
        계약·이슈는 회사 공용 자산 — 전사 조회(가시성 미적용). 입구 2개: 완료 프로젝트 이관 + 직접 등록(OEM 등 원천 프로젝트 없는 계약).
      </p>
      <div className="toolbar">
        <input placeholder="계약사·계약명 검색" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 220 }} />
        <select value={status} onChange={(e) => setStatus(e.target.value as ContractStatus | '')}>
          <option value="">상태 전체</option>
          {(['예정', '신규', '유지', '종료'] as const).map((x) => <option key={x} value={x}>{x}</option>)}
        </select>
        <label style={{ fontSize: 13 }}>종료일 ~ <input type="date" value={endBefore} onChange={(e) => setEndBefore(e.target.value)} /></label>
        <span style={{ flex: 1 }} />
        {orgCanManageContract(me, s.roleGroups) && <button className="btn primary" onClick={() => setShowNew(true)}>+ 계약 등록</button>}
      </div>
      <div className="card">
        {rows.length === 0 ? <Empty>조건에 맞는 계약이 없습니다.</Empty> : (
          <table>
            <thead>
              <tr><th>계약명</th><th>계약사</th><th>상태</th><th>기간</th><th>연 계약금액</th><th>사이트</th><th>열린 이슈</th><th>원천</th></tr>
            </thead>
            <tbody>
              {rows.map((c) => {
                const siteIds = s.sites.filter((x) => x.contractId === c.id).map((x) => x.id)
                const openIssues = s.issues.filter((i) => siteIds.includes(i.siteId) && i.status !== '완료').length
                return (
                  <tr key={c.id} className="clickable" onClick={() => nav(`/maintenance/contracts/${c.id}`)}>
                    <td><Link to={`/maintenance/contracts/${c.id}`}>{c.name}</Link></td>
                    <td>{c.counterparty}</td>
                    <td><ContractBadge status={c.status} /></td>
                    <td style={{ fontSize: 12, color: 'var(--muted)' }}>{c.startDate} ~ {c.endDate}</td>
                    <td>{c.amount.toLocaleString()}원</td>
                    <td>{siteIds.length}</td>
                    <td>{openIssues > 0 ? <span className="badge red">{openIssues}</span> : '—'}</td>
                    <td>{c.sourceProjectId ? <span className="badge blue">프로젝트 이관</span> : <span className="badge gray">직접 등록</span>}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
      {showNew && <ContractModal onClose={() => setShowNew(false)} />}
    </>
  )
}

export function ContractModal({ contract, onClose }: { contract?: MaintenanceContract; onClose: () => void }) {
  const s = useApp()
  const [form, setForm] = useState({
    name: contract?.name ?? '', counterparty: contract?.counterparty ?? '',
    status: contract?.status ?? ('예정' as ContractStatus),
    contractDate: contract?.contractDate ?? '2026-08-09',
    startDate: contract?.startDate ?? '2026-09-01', endDate: contract?.endDate ?? '2027-08-31',
    amount: contract?.amount ?? 0, monthlyAmount: contract?.monthlyAmount ?? 0,
    salesRepId: contract?.salesRepId ?? null,
    inspectionNote: contract?.inspectionNote ?? '', note: contract?.note ?? '',
  })
  const [msg, setMsg] = useState('')
  const set = (k: string, v: unknown) => setForm({ ...form, [k]: v })
  const submit = () => {
    const r = saveContract({ ...form, sourceProjectId: contract?.sourceProjectId ?? null, id: contract?.id })
    if (!r.ok) { setMsg(`${r.code}: ${r.message}`); return }
    onClose()
  }
  return (
    <Modal title={contract ? '계약 수정' : '계약 직접 등록 (OEM 등 — 원천 프로젝트 없음)'} onClose={onClose}>
      <div className="grid cols-2">
        <Field label="계약명 *"><input value={form.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label="계약사 *"><input value={form.counterparty} onChange={(e) => set('counterparty', e.target.value)} /></Field>
        <Field label="상태 — 삭제 없음: 종료는 상태로 (연 단위 갱신 이력 보존)">
          <select value={form.status} onChange={(e) => set('status', e.target.value)}>
            {(['예정', '신규', '유지', '종료'] as const).map((x) => <option key={x} value={x}>{x}</option>)}
          </select>
        </Field>
        <Field label="영업대표">
          <select value={form.salesRepId ?? 0} onChange={(e) => set('salesRepId', Number(e.target.value) || null)}>
            <option value={0}>없음</option>
            {s.people.filter((p) => p.active && !p.isSystem).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </Field>
        <Field label="시작일"><input type="date" value={form.startDate} onChange={(e) => set('startDate', e.target.value)} /></Field>
        <Field label="종료일"><input type="date" value={form.endDate} onChange={(e) => set('endDate', e.target.value)} /></Field>
        <Field label="연 계약금액(원)"><input type="number" value={form.amount} onChange={(e) => set('amount', Number(e.target.value))} /></Field>
        <Field label="월간금액(원)"><input type="number" value={form.monthlyAmount} onChange={(e) => set('monthlyAmount', Number(e.target.value))} /></Field>
      </div>
      <Field label="정기점검 (정보 텍스트 — 일정 엔진·자동 이슈 생성 없음)">
        <input value={form.inspectionNote} onChange={(e) => set('inspectionNote', e.target.value)} placeholder="예: 분기 1회(1·4·7·10월)" />
      </Field>
      <Field label="비고"><textarea rows={2} value={form.note} onChange={(e) => set('note', e.target.value)} /></Field>
      {msg && <ErrorBox>{msg}</ErrorBox>}
      <div className="actions">
        <button className="btn" onClick={onClose}>취소</button>
        <button className="btn primary" onClick={submit}>저장</button>
      </div>
    </Modal>
  )
}
