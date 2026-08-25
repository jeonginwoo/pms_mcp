/*
 * 프로젝트 목록 (AC A3-1) — 가시성 범위는 서버가 걸러 준 것을 그대로 보여 준다.
 *
 * **phase 탭은 서버가 거른다**(§7 `?phase=`, v2.4): 탭을 고르면 그 그룹의 전량을
 * 다시 받아 온다. 상태 칩·이름 검색은 받아 둔 것 위에서 화면이 거른다 — 서버에
 * 그 둘의 필터가 아직 없어서다(`api.ts`의 목록 전략 주석이 되돌리기 조건을 갖는다).
 *
 * 탭이 status가 아니라 phase인 것이 핵심이다: 두 축이 다르므로 겹쳐 걸 수 있고
 * (솔루션 탭 + 완료 칩), 유지보수중은 어느 탭에도 없다(§5 — 유지보수 탭의 원천은
 * 프로젝트가 아니라 계약이다). 그래서 "전체"가 그것을 볼 수 있는 유일한 자리다.
 */
import { useEffect, useMemo, useState } from 'react'
import { useStore } from '../store'
import { PHASE_LABEL, PHASE_ORDER, STATUS_LABEL, STATUS_ORDER } from '../labels'
import { Bar, Empty, StatusBadge } from '../components/ui'
import ProjectCreateModal from '../components/ProjectCreateModal'
import type { ProjectPhase, ProjectStatus, ProjectSummary } from '../types/api'

const GRID = 'minmax(0,2fr) minmax(90px,120px) 84px minmax(70px,90px) minmax(90px,1fr)'

/*
 * 탭 이름은 `labels.ts`의 phase 표에서 온다 — 여기에 다시 적으면 서버 열거의
 * 한국어 라벨이 두 벌이 되고, 그것이 이 변경에서 `ProjectPhase`를 한 벌로
 * 만든 이유와 정반대가 된다(상세 화면은 이미 `PHASE_LABEL`을 쓴다).
 * "전체"만 여기 있다: phase 값이 아니라 필터를 걸지 않는다는 뜻이다.
 */
const PHASE_TABS: { key: ProjectPhase | 'ALL'; label: string }[] = [
  { key: 'ALL', label: '전체' },
  ...PHASE_ORDER.map((phase) => ({ key: phase, label: PHASE_LABEL[phase] })),
]

/**
 * phase 탭 하나의 목록 — `null`이면 아직 받는 중이다(빈 배열과 구별해야 한다).
 *
 * `total`을 함께 들고 있는 것이 요점이다: 무필터 목록이 그것으로 절단 경고를
 * 띄우는데, 탭 경로가 응답의 `totalElements`를 버리면 **한 그룹이 페이지 크기를
 * 넘는 날 경고 없이 잘린다** — 그 위에서 도는 상태 칩·검색이 "없습니다"를 내놓고
 * 사용자는 그것을 참으로 읽는다.
 */
interface PhaseRows {
  rows: ProjectSummary[]
  total: number
  error: string | null
}

/*
 * `projects`(store의 무필터 전량)를 의존성에 두는 것이 쓰기 반영의 경로다 —
 * 등록·상태 전이 뒤 store가 목록을 다시 읽으면 이 effect가 함께 깨어나 탭도
 * 최신이 된다. 그러지 않으면 새 프로젝트가 "전체"에만 보인다.
 */
function usePhaseRows(phase: ProjectPhase | 'ALL'): PhaseRows | null {
  const { projects, loadProjects } = useStore()
  const [loaded, setLoaded] = useState<PhaseRows | null>(null)

  useEffect(() => {
    if (phase === 'ALL') {
      setLoaded(null)

      return
    }

    let live = true
    // 직전 탭의 행을 비워 두고 받는다 — 남겨 두면 잠깐 **다른 탭의 목록**이 보이고,
    // 그 사이 상태 칩·검색은 그 위에서 돌아 사용자가 틀린 결과를 참으로 읽는다
    setLoaded(null)
    void loadProjects(phase).then((result) => {
      if (!live) {
        return
      }

      setLoaded(result.ok
        ? { rows: result.value.content, total: result.value.totalElements, error: null }
        // 실패했는데 직전 탭의 행을 그대로 두면 틀린 탭을 보여 주게 된다
        : { rows: [], total: 0, error: result.error.message })
    })

    return () => { live = false }
  }, [phase, projects, loadProjects])

  return loaded
}

export default function Projects() {
  const { projects, totalProjects, openProject, me } = useStore()
  const [phase, setPhase] = useState<ProjectPhase | 'ALL'>('ALL')
  const [status, setStatus] = useState<ProjectStatus | 'ALL'>('ALL')
  const [keyword, setKeyword] = useState('')
  const [creating, setCreating] = useState(false)
  const tab = usePhaseRows(phase)

  // 탭을 받아 오는 중 — 빈 배열과 구별해야 한다. 둘을 같이 다루면 로딩 중에
  // "없습니다"가 스쳐 지나가고, 그것은 사용자가 참으로 읽는 틀린 답이다
  const loadingTab = phase !== 'ALL' && tab === null
  const tabError = tab?.error ?? null
  const rows = phase === 'ALL' ? projects : tab?.rows ?? []
  // 절단 경고의 기준 — 두 경로가 같은 규칙을 쓴다
  const total = phase === 'ALL' ? totalProjects : tab?.total ?? 0

  const filtered = useMemo(() => {
    const needle = keyword.trim()

    return rows.filter((project) =>
      (status === 'ALL' || project.status === status)
      && (needle === ''
        || project.name.includes(needle)
        || project.client.includes(needle)))
  }, [rows, status, keyword])

  return (
    <section className="card">
      <div className="card-head">
        <h2>
          프로젝트{' '}
          <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
            {loadingTab
              ? '불러오는 중…'
              : `${filtered.length}건${filtered.length !== rows.length ? ` / ${rows.length}건` : ''}`}
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

      {/*
        두 줄이 같은 칩 모양이고 각자 "전체"를 갖는다 — 축 이름을 붙여 두지 않으면
        아래 "전체"를 눌러 탭이 풀릴 것으로 기대하는 오조작이 자연스럽다.
        두 축은 겹쳐 걸린다(솔루션 탭 + 완료 칩)는 것이 이 화면의 성질이다.
      */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
        <span className="muted2" style={{ fontSize: 11.5, minWidth: 30 }}>단계</span>
        {PHASE_TABS.map((option) => (
          <button key={option.key} className={`chip-btn ${phase === option.key ? 'on' : ''}`}
            onClick={() => setPhase(option.key)}>
            {option.label}
          </button>
        ))}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginBottom: 14 }}>
        <span className="muted2" style={{ fontSize: 11.5, minWidth: 30 }}>상태</span>
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
              {/* 완료 건이 솔루션 탭에 남아 있는 이유를 그 자리에서 말해 준다 (부록 A) */}
              {project.status === 'COMPLETED' && (
                <span className="muted2" style={{ fontWeight: 500, fontSize: 11, marginLeft: 6 }}>
                  이관 대기
                </span>
              )}
            </div>
          </div>
          <span className="muted">{project.client}</span>
          <StatusBadge status={project.status} />
          <span className="muted">{project.managerName ?? '—'}</span>
          <Bar value={project.progress} done={project.status === 'COMPLETED'} />
        </button>
      ))}

      {tabError && <Empty>목록을 불러오지 못했습니다 — {tabError}</Empty>}

      {!tabError && !loadingTab && filtered.length === 0
        && <Empty>조건에 맞는 프로젝트가 없습니다.</Empty>}

      {/*
        절단 경고는 두 경로 모두에 걸린다 — 탭 경로에서 빼면 한 그룹이 페이지 크기를
        넘는 날 조용히 잘리고, 그 위에서 도는 상태 칩·검색이 "없습니다"를 내놓는다.
      */}
      {!loadingTab && total > rows.length && (
        <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
          {phase === 'ALL' ? '전체' : `${PHASE_LABEL[phase]} 단계`} {total}건 중 {rows.length}건만
          받아 왔습니다 — 서버 검색·페이징 연동 전 임시 상태입니다.
        </div>
      )}

      {creating && <ProjectCreateModal onClose={() => setCreating(false)} />}
    </section>
  )
}
