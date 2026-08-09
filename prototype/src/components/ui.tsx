// 공용 소품 — 뱃지·모달·페이지네이션·빈 상태
import type { ReactNode } from 'react'
import type { ContractStatus, IssueStatus, ProjectRole, ProjectStatus } from '../types'

export function StatusBadge({ status }: { status: ProjectStatus }) {
  const color: Record<ProjectStatus, string> = {
    계약대기: 'gray', 수주확정: 'amber', 진행중: 'blue', 완료: 'green', 유지보수중: 'purple',
  }
  return <span className={`badge ${color[status]}`}>{status}</span>
}

export function RoleBadge({ role }: { role: ProjectRole }) {
  const color = role === 'PM' ? 'red' : role === 'PL' ? 'amber' : 'gray'
  const label = role === 'PARTICIPANT' ? '참여자' : role
  return <span className={`badge ${color}`}>{label}</span>
}

export function ContractBadge({ status }: { status: ContractStatus }) {
  const color: Record<ContractStatus, string> = { 예정: 'gray', 신규: 'blue', 유지: 'green', 종료: 'red' }
  return <span className={`badge ${color[status]}`}>{status}</span>
}

export function IssueBadge({ status }: { status: IssueStatus }) {
  const color: Record<IssueStatus, string> = { 접수: 'red', 처리중: 'blue', 고객확인대기: 'amber', 완료: 'green' }
  return <span className={`badge ${color[status]}`}>{status}</span>
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-back" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{title}</h3>
        {children}
      </div>
    </div>
  )
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
    </div>
  )
}

export function Empty({ children = '데이터가 없습니다.' }: { children?: ReactNode }) {
  return <div className="empty">{children}</div>
}

export function ErrorBox({ children }: { children: ReactNode }) {
  return <div className="error-box">{children}</div>
}

export function InfoBox({ children }: { children: ReactNode }) {
  return <div className="info-box">{children}</div>
}

export function ProgressBar({ value }: { value: number }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <div className="progress-track" style={{ maxWidth: 140 }}>
        <div className={`progress-bar ${value >= 100 ? 'full' : ''}`} style={{ width: `${Math.min(value, 100)}%` }} />
      </div>
      <span style={{ fontSize: 12.5, color: 'var(--muted)', width: 34 }}>{value}%</span>
    </div>
  )
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div className="pagination">
      <button className="btn sm" disabled={page === 0} onClick={() => onChange(page - 1)}>이전</button>
      <span style={{ fontSize: 12.5, color: 'var(--muted)' }}>{page + 1} / {totalPages}</span>
      <button className="btn sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>다음</button>
    </div>
  )
}
