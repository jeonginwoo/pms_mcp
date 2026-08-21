/*
 * 프로젝트 목록 (AC A3-1) — 가시성 범위는 서버가 걸러 준 것을 그대로 보여 준다.
 *
 * 상태 칩·검색은 받아 둔 목록에서 화면이 거른다: 서버에 이름 검색·phase 필터가
 * 아직 없다(§7의 `?phase=`도 미구현). 서버 필터가 생기면 질의로 내려보낸다.
 */
import { useMemo, useState } from 'react'
import { useStore } from '../store'
import { STATUS_LABEL, STATUS_ORDER } from '../labels'
import { Bar, Empty, StatusBadge } from '../components/ui'
import ProjectCreateModal from '../components/ProjectCreateModal'
import type { ProjectStatus } from '../types/api'

const GRID = 'minmax(0,2fr) minmax(90px,120px) 84px minmax(70px,90px) minmax(90px,1fr)'

export default function Projects() {
  const { projects, totalProjects, openProject, me } = useStore()
  const [status, setStatus] = useState<ProjectStatus | 'ALL'>('ALL')
  const [keyword, setKeyword] = useState('')
  const [creating, setCreating] = useState(false)

  const filtered = useMemo(() => {
    const needle = keyword.trim()

    return projects.filter((project) =>
      (status === 'ALL' || project.status === status)
      && (needle === ''
        || project.name.includes(needle)
        || project.client.includes(needle)))
  }, [projects, status, keyword])

  return (
    <section className="card">
      <div className="card-head">
        <h2>
          프로젝트{' '}
          <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
            {filtered.length}건{filtered.length !== projects.length && ` / ${projects.length}건`}
          </span>
        </h2>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <input placeholder="프로젝트 · 고객사 검색" value={keyword}
            onChange={(e) => setKeyword(e.target.value)} style={{ width: 220 }} />
          {/* 생성은 권한 그룹의 "프로젝트 생성" 플래그가 판정한다 (A1-5) */}
          {me?.createProject && (
            <button className="btn btn-primary" onClick={() => setCreating(true)}>
              + 새 프로젝트
            </button>
          )}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 14 }}>
        <button className={`chip-btn ${status === 'ALL' ? 'on' : ''}`}
          onClick={() => setStatus('ALL')}>전체</button>
        {STATUS_ORDER.map((value) => (
          <button key={value} className={`chip-btn ${status === value ? 'on' : ''}`}
            onClick={() => setStatus(value)}>
            {STATUS_LABEL[value]}
          </button>
        ))}
      </div>

      <div className="thead" style={{ gridTemplateColumns: GRID }}>
        <span>프로젝트</span>
        <span>고객사</span>
        <span>상태</span>
        <span>담당 PM</span>
        <span>진행률</span>
      </div>

      {filtered.map((project) => (
        <button key={project.id} className="trow"
          style={{ gridTemplateColumns: GRID, padding: '12px 2px' }}
          onClick={() => void openProject(project.id)}>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {project.name}
            </div>
          </div>
          <span className="muted">{project.client}</span>
          <StatusBadge status={project.status} />
          <span className="muted">{project.managerName ?? '—'}</span>
          <Bar value={project.progress} done={project.status === 'COMPLETED'} />
        </button>
      ))}

      {filtered.length === 0 && <Empty>조건에 맞는 프로젝트가 없습니다.</Empty>}

      {totalProjects > projects.length && (
        <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
          전체 {totalProjects}건 중 {projects.length}건만 받아 왔습니다 — 서버 검색·페이징 연동 전
          임시 상태입니다.
        </div>
      )}

      {creating && <ProjectCreateModal onClose={() => setCreating(false)} />}
    </section>
  )
}
