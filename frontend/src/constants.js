/*
 * 화면 공용 상수 — 데이터는 전부 백엔드에서 로드한다 (시드 출처: 사원정보.txt · proten_projects.xlsx).
 * 이 파일에는 달력 유틸과 표시용 상수만 둔다. (구 data/seed.js의 목업 데이터는 제거됨)
 */

// ── 월 계산 유틸 ──
const now = new Date();
export const THIS_YM = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;

export function addYm(ym, n) {
  const [y, m] = ym.split('-').map(Number);
  const t = y * 12 + (m - 1) + n;
  return `${Math.floor(t / 12)}-${String((t % 12) + 1).padStart(2, '0')}`;
}
export const NEXT_YM = addYm(THIS_YM, 1);
export function ymLabel(ym) { const [y, m] = ym.split('-'); return `${y}년 ${Number(m)}월`; }

/** 가동률 집계 제외 팀 — 전사 직속(대표이사). 사원정보.txt 최상위 '프로텐' 소속 */
export const HQ_TEAM = '프로텐';

/** 수행 형태 라벨 (proten_projects.xlsx 개발장소 컬럼) */
export const ENGAGEMENT_LABEL = { ONSITE: '상주', PARTIAL_ONSITE: '부분상주', REMOTE: '원격', OFFSITE: '오프사이트' };

/**
 * 권한(역할) 정의 — UI 노출 제어용 (서버 OrgRole과 대응, 최종 판정은 서버).
 * 사원정보.txt 조직 기준: 부서 직속 최고직급=부서장 · 팀 최고직급=팀장 · 대표이사=관리자
 */
export const ROLES = [
  { key: 'DIVISION_HEAD', label: '부서장', desc: '부서 전체 조회 · 부서 프로젝트 관리', fixed: true, perms: { viewAll: true, createProj: true, manageProj: true, editProgress: false, manageOrg: false } },
  { key: 'TEAM_LEAD', label: '팀장', desc: '소속 팀 조회 · 팀 프로젝트 관리', fixed: true, perms: { viewAll: false, createProj: true, manageProj: true, editProgress: true, manageOrg: false } },
  { key: 'MEMBER', label: '팀원', desc: '본인 참여 프로젝트 조회 · 진행률 수정', fixed: true, perms: { viewAll: false, createProj: false, manageProj: false, editProgress: true, manageOrg: false } },
  { key: 'ADMIN', label: '관리자', desc: '전체 관리 · 사용자/조직/권한 관리', fixed: true, perms: { viewAll: true, createProj: true, manageProj: true, editProgress: true, manageOrg: true } },
];

/** 로그인 화면 데모 계정 (시드 가명 계정 — 초기 비밀번호 proten1!) */
export const DEMO_ACCOUNTS = [
  { name: '정태휘', roleLabel: '부서장 · AX솔루션사업부', email: 'ozuhnww23@proten.co.kr', badge: ['rgba(107,91,210,.14)', '#6b5bd2'] },
  { name: '남도린', roleLabel: '팀장 · AX솔루션개발1팀', email: 'nrnhnnv26@proten.co.kr', badge: ['rgba(61,99,216,.13)', '#3d63d8'] },
  { name: '양시온', roleLabel: '팀원 · AX솔루션개발2팀', email: 'ezrc74@proten.co.kr', badge: ['rgba(31,138,76,.13)', '#1f8a4c'] },
  { name: '신현랑', roleLabel: '관리자 · 대표이사', email: 'mgecul13@proten.co.kr', badge: ['rgba(194,97,30,.13)', '#c2611e'] },
];
export const DEMO_PASSWORD = 'proten1!';

/** MCP 도구 카탈로그 (설정·관리 표시용 — 실제 노출 도구와 동일 명세) */
export const MCP_TOOLS = [
  { name: 'whoami', desc: '내 계정·가시성 확인', scope: '조회' },
  { name: 'find_person', desc: '인물 검색 (조직 가시성 내)', scope: '조회' },
  { name: 'search_projects', desc: '프로젝트 검색·상세', scope: '조회' },
  { name: 'get_utilization', desc: '월별 가동률 (개인·팀·부서)', scope: '조회' },
  { name: 'list_overbooked', desc: '보정 100% 초과 인원 + 원인', scope: '조회' },
  { name: 'list_maintenance_logs', desc: '계약별 이력 (최근 50건)', scope: '조회' },
  { name: 'update_progress', desc: '본인 프로젝트 진척률 — 2단계 확인', scope: '쓰기' },
];
