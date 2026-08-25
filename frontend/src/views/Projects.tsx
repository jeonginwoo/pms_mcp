/*
 * 프로젝트 목록 (AC A3-1) — 가시성 범위는 서버가 걸러 준 것을 그대로 보여 준다.
 *
 * **한 화면이 phase 하나를 든다**(2026-08-25 사용자 결정): 사이드바의 "영업"과
 * "프로젝트"가 각각 `?phase=SALES`·`?phase=SOLUTION`이고, 화면 안에 단계 탭은 없다.
 * 두 화면은 겹치지 않으며 **유지보수중은 어느 쪽에도 없다** — §5가 "status=유지보수중
 * 프로젝트의 화면 노출은 연결된 계약이 담당한다"고 이미 정했고, 이 분할이 그 문면
 * 그대로다(그전의 "전체" 탭이 오히려 그것을 어겼다).
 *
 * 상태 칩·이름 검색은 받아 둔 것 위에서 화면이 거른다 — 서버에 그 둘의 필터가 아직
 * 없어서다(`api.ts`의 목록 전략 주석이 되돌리기 조건을 갖는다).
 */
import { useEffect, useMemo, useState } from 'react'
import { useStore } from '../store'
import { STATUS_LABEL, STATUS_ORDER } from '../labels'
import { Bar, Empty, StatusBadge } from '../components/ui'
import ProjectCreateModal from '../components/ProjectCreateModal'
import type { ProjectPhase, ProjectStatus, ProjectSummary } from '../types/api'

const GRID = 'minmax(0,2fr) minmax(90px,120px) 84px minmax(70px,90px) minmax(90px,1fr)'

/**
 * 이 화면이 든 phase의 목록 — `null`이면 아직 받는 중이다(빈 배열과 구별해야 한다).
 *
 * `total`을 함께 들고 있는 것이 요점이다: 응답의 `totalElements`를 버리면 **한 그룹이
 * 페이지 크기를 넘는 날 경고 없이 잘린다** — 그 위에서 도는 상태 칩·검색이 "없습니다"를
 * 내놓고 사용자는 그것을 참으로 읽는다.
 */
interface PhaseRows {
  rows: ProjectSummary[]
  total: number
  error: string | null
}

/*
 * `projects`(store의 무필터 전량)를 의존성에 두는 것이 쓰기 반영의 경로다 —
 * 등록·상태 전이 뒤 store가 목록을 다시 읽으면 이 effect가 함께 깨어나 이 화면도
 * 최신이 된다. 그러지 않으면 새 프로젝트가 홈의 집계에만 나타난다.
 */
function usePhaseRows(phase: ProjectPhase): PhaseRows | null {
  const { projects, loadProjects } = useStore()
  const [loaded, setLoaded] = useState<PhaseRows | null>(null)

  useEffect(() => {
    let live = true
    // 직전 화면의 행을 비워 두고 받는다 — 남겨 두면 잠깐 **다른 phase의 목록**이 보이고,
    // 그 사이 상태 칩·검색은 그 위에서 돌아 사용자가 틀린 결과를 참으로 읽는다
    setLoaded(null)
    void loadProjects(phase).then((result) => {
      if (!live) {
        return
      }

      setLoaded(result.ok
        ? { rows: result.value.content, total: result.value.totalElements, error: null }
        // 실패했는데 직전 화면의 행을 그대로 두면 틀린 목록을 보여 주게 된다
        : { rows: [], total: 0, error: result.error.message })
    })

    return () => { live = false }
  }, [phase, projects, loadProjects])

  return loaded
}

interface Props {
  /** 이 화면이 서버에서 받아 올 그룹 (§7 `?phase=`) */
  phase: ProjectPhase
  /** 화면 이름 — 사이드바 항목과 같은 낱말이다("프로젝트"는 솔루션 그룹의 이름이 아니다) */
  title: string
}

export default function Projects({ phase, title }: Props) {
  const { openProject, me } = useStore()
  const [status, setStatus] = useState<ProjectStatus | 'ALL'>('ALL')
  const [keyword, setKeyword] = useState('')
  const [creating, setCreating] = useState(false)
  const loaded = usePhaseRows(phase)

  // 받아 오는 중 — 빈 배열과 구별해야 한다. 둘을 같이 다루면 로딩 중에
  // "없습니다"가 스쳐 지나가고, 그것은 사용자가 참으로 읽는 틀린 답이다
  const loading = loaded === null
  const error = loaded?.error ?? null
  const rows = loaded?.rows ?? []
  const total = loaded?.total ?? 0

  /*
   * 상태 칩은 **받아 온 행에서** 만든다. phase → status 표를 여기 적으면 서버가
   * 가진 그 표의 사본이 화면에 생기고(§5가 금지한 이중화), 전부 싣자니 이 화면에
   * 결코 나올 수 없는 상태(예: 영업 화면의 "완료")가 죽은 칩으로 남는다.
   * 목록이 그 그룹의 **전량**이므로 여기서 세는 것이 그 그룹의 상태 전부다.
   */
  const statusChips = useMemo(
    () => STATUS_ORDER.filter((value) => rows.some((project) => project.status === value)),
    [rows])

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
          {title}{' '}
          <span className="muted2" style={{ fontWeight: 500, fontSize: 12.5 }}>
            {loading
              ? '불러오는 중…'
              : `${filtered.length}건${filtered.length !== rows.length ? ` / ${rows.length}건` : ''}`}
          </span>
        </h2>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <input placeholder="프로젝트 · 고객사 검색" value={keyword}
            onChange={(e) => setKeyword(e.target.value)} style={{ width: 220 }} />
          {/*
            생성은 권한 그룹의 "프로젝트 생성" 플래그가 판정한다 (A1-5). 버튼이 영업
            화면에만 있는 것은 신규 프로젝트가 **계약대기**로 태어나기 때문이다 —
            솔루션 화면에 두면 만든 것이 그 목록에 나타나지 않는다.
          */}
          {me?.createProject && phase === 'SALES' && (
            <button className="btn btn-primary" onClick={() => setCreating(true)}>
              + 새 프로젝트
            </button>
          )}
        </div>
      </div>

      {/* 칩이 하나뿐이면(= 그 그룹에 상태가 한 종류) 걸 것이 없어 줄을 내지 않는다 */}
      {statusChips.length > 1 && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginBottom: 14 }}>
          <span className="muted2" style={{ fontSize: 11.5, minWidth: 30 }}>상태</span>
          <button className={`chip-btn ${status === 'ALL' ? 'on' : ''}`}
            onClick={() => setStatus('ALL')}>전체</button>
          {statusChips.map((value) => (
            <button key={value} className={`chip-btn ${status === value ? 'on' : ''}`}
              onClick={() => setStatus(value)}>
              {STATUS_LABEL[value]}
            </button>
          ))}
        </div>
      )}

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
              {/* 완료 건이 이 목록에 남아 있는 이유를 그 자리에서 말해 준다 (부록 A) */}
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

      {error && <Empty>목록을 불러오지 못했습니다 — {error}</Empty>}

      {!error && !loading && filtered.length === 0
        && <Empty>조건에 맞는 프로젝트가 없습니다.</Empty>}

      {/* 절단 경고 — 없으면 한 그룹이 페이지 크기를 넘는 날 조용히 잘린다 */}
      {!loading && total > rows.length && (
        <div className="muted2" style={{ fontSize: 11.5, marginTop: 12 }}>
          {title} {total}건 중 {rows.length}건만 받아 왔습니다 — 서버 검색·페이징 연동 전
          임시 상태입니다.
        </div>
      )}

      {creating && <ProjectCreateModal onClose={() => setCreating(false)} />}
    </section>
  )
}
