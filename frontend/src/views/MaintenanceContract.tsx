/*
 * 유지보수 계약 상세 (부록 A `/maintenance/contracts/:id` · AC D4-2).
 *
 * 계약 정보 · 사이트 목록(솔루션 버전·서버스펙·담당 엔지니어) · 연락처 · 이슈 요약.
 * `sourceProjectId`는 **이관으로 생긴 계약에만** 있다(OEM 직접 등록 계약은 원천
 * 프로젝트가 없다) — 없으면 링크 자체를 그리지 않는다.
 *
 * 시트에서 온 값을 지우지 않는다: `contractDateNote`(비날짜 계약일 원문)·`note`(적재
 * 보정 원문)·연락처 `raw`는 구조화에 실패했거나 모순이 있던 원문이고, 화면에서
 * 빼면 그 정보는 아무 데서도 볼 수 없다.
 */
import { useState } from 'react'
import { useStore } from '../store'
import { Empty, Metric } from '../components/ui'
import ContractEditModal from '../components/ContractEditModal'
import SiteEditModal from '../components/SiteEditModal'
import IssueRegisterModal from '../components/IssueRegisterModal'
import { period, shortDate } from '../labels'
import type { SiteView } from '../types/api'

export default function MaintenanceContract() {
  const { me, contract, closeContract, openProject, openContract } = useStore()
  const [editing, setEditing] = useState(false)
  // undefined = 닫힘 · null = 신규 등록 · SiteView = 그 사이트 수정
  const [siteDraft, setSiteDraft] = useState<SiteView | null | undefined>(undefined)
  // 이 계약에 이슈를 등록한다 — 사이트가 이미 여기 있어 선택이 한 단계다(D3-1)
  const [registeringIssue, setRegisteringIssue] = useState(false)
  const writable = me?.manageContracts === true

  if (!contract) {
    return <Empty>계약을 선택하세요.</Empty>
  }

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <section className="card">
        <div className="card-head">
          <div style={{ minWidth: 0 }}>
            <button className="btn btn-ghost btn-sm" onClick={closeContract}
              style={{ marginBottom: 8 }}>← 목록</button>
            <h2 style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              {contract.name}
              <span className="badge" style={{ fontSize: 11.5 }}>{contract.status}</span>
            </h2>
            <div className="muted" style={{ fontSize: 12.5, marginTop: 2 }}>
              {contract.contractor}
              {contract.sheetSection && (
                <span className="muted2"> · 시트 {contract.sheetSection}</span>
              )}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            {/* 이관으로 생긴 계약만 원천 프로젝트를 갖는다 (D1) */}
            {contract.sourceProjectId !== null && (
              <button className="btn btn-ghost"
                onClick={() => void openProject(contract.sourceProjectId as number)}>
                원 프로젝트 열기
              </button>
            )}
            {writable && (
              <button className="btn btn-ghost" onClick={() => setEditing(true)}>
                계약 수정
              </button>
            )}
          </div>
        </div>

        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14 }}>
          <Metric label="기간" value={period(contract.startDate, contract.endDate)} />
          <Metric label="사이트" value={`${contract.sites.length}곳`} />
          <Metric label="계약 금액" value={money(contract.amount)} />
          <Metric label="월 금액" value={money(contract.monthlyAmount)} />
        </div>

        <div style={{ display: 'grid', gap: 6 }}>
          <Row label="계약일" value={contract.contractDate
            ? shortDate(contract.contractDate)
            : contract.contractDateNote} />
          <Row label="영업대표" value={contract.salesRep?.name} />
          <Row label="분류" value={contract.category} />
          <Row label="대상" value={contract.targetInfra} />
          <Row label="정기점검" value={contract.regularCheck} />
          <Row label="비고" value={contract.note} />
        </div>

        {Object.keys(contract.issueCountByStatus).length > 0 && (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 14 }}>
            {Object.entries(contract.issueCountByStatus).map(([status, count]) => (
              <span key={status} className="badge" style={{ fontSize: 11.5 }}>
                이슈 {status} {count}건
              </span>
            ))}
          </div>
        )}
      </section>

      <Sites sites={contract.sites} writable={writable} onAddIssue={() => setRegisteringIssue(true)}
        onAdd={() => setSiteDraft(null)} onEdit={setSiteDraft} />

      {editing && (
        <ContractEditModal contract={contract} onClose={() => setEditing(false)} />
      )}
      {registeringIssue && (
        <IssueRegisterModal contractId={contract.id}
          onClose={() => setRegisteringIssue(false)}
          onRegistered={() => openContract(contract.id)} />
      )}
      {siteDraft !== undefined && (
        <SiteEditModal contractId={contract.id} site={siteDraft}
          onClose={() => setSiteDraft(undefined)} />
      )}
    </div>
  )
}

/**
 * 사이트 목록 — **이슈 등록 버튼이 여기 있다**: 이슈는 사이트에 붙으므로(D3-1) 계약
 * 상세에서 열면 사이트 선택이 한 단계로 끝난다. 그 버튼은 계약 쓰기와 달리
 * `writable`을 보지 않는다 — US-D3은 로그인 사용자 전체다.
 */
function Sites({ sites, writable, onAdd, onEdit, onAddIssue }: {
  sites: SiteView[]
  writable: boolean
  onAdd: () => void
  onEdit: (site: SiteView) => void
  onAddIssue: () => void
}) {
  return (
    <section className="card">
      <div className="card-head">
        <h3>사이트 <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
          {sites.length}곳
        </span></h3>
        <div style={{ display: 'flex', gap: 6 }}>
          <button className="btn btn-ghost btn-sm" onClick={onAddIssue}>+ 이슈 등록</button>
          {writable && (
            <button className="btn btn-primary btn-sm" onClick={onAdd}>+ 사이트 등록</button>
          )}
        </div>
      </div>

      {sites.map((site) => (
        <div key={site.id} style={{ padding: '12px 2px', borderTop: '1px solid var(--border-soft)' }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'baseline', flexWrap: 'wrap' }}>
            <span style={{ fontWeight: 700 }}>{site.name}</span>
            {site.channel && <span className="badge" style={{ fontSize: 11 }}>{site.channel}</span>}
            <span className="muted2" style={{ fontSize: 12 }}>
              담당 {site.engineer?.name ?? '미배정'}
            </span>
            {writable && (
              <button className="btn btn-ghost btn-sm" style={{ marginLeft: 'auto' }}
                onClick={() => onEdit(site)}>
                수정
              </button>
            )}
          </div>

          {site.serverSpec && (
            <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>{site.serverSpec}</div>
          )}

          {site.contacts.length > 0 && (
            <div style={{ display: 'grid', gap: 3, marginTop: 6 }}>
              {site.contacts.map((contact) => (
                <div key={contact.id} className="muted2" style={{ fontSize: 12 }}>
                  <span className="badge" style={{ fontSize: 10.5, marginRight: 6 }}>
                    {contact.party}
                  </span>
                  {/* 구조화에 실패한 연락처는 시트 원문(raw)을 그대로 보여 준다 */}
                  {contact.name
                    ? [contact.name, contact.title, contact.phone, contact.email]
                      .filter(Boolean).join(' · ')
                    : contact.raw}
                </div>
              ))}
            </div>
          )}
        </div>
      ))}

      {sites.length === 0 && <Empty>등록된 사이트가 없습니다.</Empty>}
    </section>
  )
}

/** 값이 없는 줄은 그리지 않는다 — 시트에서 온 계약이라 비어 있는 칸이 많다. */
function Row({ label, value }: { label: string; value: string | null | undefined }) {
  if (!value) {
    return null
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '84px minmax(0,1fr)', gap: 10, fontSize: 12.5 }}>
      <span className="muted2">{label}</span>
      <span>{value}</span>
    </div>
  )
}

function money(amount: number | null): string {
  return amount === null ? '—' : `${amount.toLocaleString('ko-KR')}원`
}
