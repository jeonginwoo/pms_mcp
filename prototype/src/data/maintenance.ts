// 유지보수 목업 데이터 — v2.4 계약/사이트/이슈 3층 화면 검증용.
// 실데이터("2026 기술지원 및 유지보수" 시트)는 시드 적재 시 변환(부록 B) — 여기는 구조 검증용 가상본.
// 실측 특성 재현: 계약:사이트 1:N(가온아이 다수 사이트) · OEM 직접 등록(원천 프로젝트 없음) · 이슈 무배정 다수
import type {
  MaintenanceContract, MaintenanceSite, MaintenanceContact, MaintenanceIssue, IssueComment,
} from '../types'

export const mockContracts: MaintenanceContract[] = [
  {
    id: 1, sourceProjectId: null, counterparty: '(주)가온아이',
    name: '2026 가온아이 그룹웨어 검색엔진 유지보수(통합)', status: '유지',
    contractDate: '2025-12-20', startDate: '2026-01-01', endDate: '2026-12-31',
    amount: 96_000_000, monthlyAmount: 8_000_000, salesRepId: 30,
    inspectionNote: '분기 1회 정기점검(1·4·7·10월), 특이사항 없으면 기록 생략',
    note: 'OEM 채널 통합 계약 — 사이트별 개별 프로젝트 없음', version: 1,
  },
  {
    id: 2, sourceProjectId: 317, counterparty: '우리에프아이에스',
    name: '2026 우리은행 문서중앙화 유지보수', status: '신규',
    contractDate: '2026-06-30', startDate: '2026-07-01', endDate: '2027-06-30',
    amount: 42_000_000, monthlyAmount: 3_500_000, salesRepId: 30,
    inspectionNote: '반기 1회 정기점검', note: '', version: 1,
  },
  {
    id: 3, sourceProjectId: null, counterparty: '(주)젠솔소프트',
    name: '2026 수출입은행 규정관리 유지보수', status: '유지',
    contractDate: '2025-12-28', startDate: '2026-01-01', endDate: '2026-12-31',
    amount: 18_000_000, monthlyAmount: 1_500_000, salesRepId: null,
    inspectionNote: '', note: 'OEM — 젠솔소프트 경유', version: 1,
  },
  {
    id: 4, sourceProjectId: null, counterparty: '한국정보기술(주)',
    name: '2025 국세청 검색 고도화 유지보수', status: '종료',
    contractDate: '2024-12-15', startDate: '2025-01-01', endDate: '2025-12-31',
    amount: 30_000_000, monthlyAmount: 2_500_000, salesRepId: 30,
    inspectionNote: '', note: '2026 갱신 협상 결렬', version: 1,
  },
  {
    id: 5, sourceProjectId: null, counterparty: '(주)비즈웰',
    name: '2026 비즈웰 그룹웨어 검색 유지보수(예정)', status: '예정',
    contractDate: '2026-08-01', startDate: '2026-09-01', endDate: '2027-08-31',
    amount: 24_000_000, monthlyAmount: 2_000_000, salesRepId: 30,
    inspectionNote: '', note: '계약서 날인 대기', version: 1,
  },
]

export const mockSites: MaintenanceSite[] = [
  // 계약 1 — 가온아이 1:N (실측 ~45사이트의 축약 6건)
  { id: 1, contractId: 1, customer: '인제대병원', solution: '검색엔진 v4.2', target: '솔루션', serverSpec: 'RHEL8 / 8core 32GB', engineerId: 17 },
  { id: 2, contractId: 1, customer: '동아대학교', solution: '검색엔진 v4.0', target: '솔루션', serverSpec: 'Windows Server 2019 / 4core 16GB', engineerId: 17 },
  { id: 3, contractId: 1, customer: '세종대학교', solution: '검색엔진 v4.2', target: '솔루션', serverSpec: 'Ubuntu 22.04 / 8core 16GB', engineerId: 19 },
  { id: 4, contractId: 1, customer: '한국해양대학교', solution: '검색엔진 v3.9', target: '인프라', serverSpec: 'CentOS7 / 4core 8GB (EOL 주의)', engineerId: 19 },
  { id: 5, contractId: 1, customer: '창원문성대학교', solution: '검색엔진 v4.2', target: '솔루션', serverSpec: 'RHEL9 / 8core 32GB', engineerId: 20 },
  { id: 6, contractId: 1, customer: '경남은행', solution: '검색엔진 v4.5', target: '솔루션', serverSpec: 'RHEL8 / 16core 64GB', engineerId: 18 },
  // 계약 2 — 이관 경로 (프로젝트 317)
  { id: 7, contractId: 2, customer: '우리은행', solution: '문서중앙화 v2.1', target: '솔루션', serverSpec: 'RHEL8 / 16core 64GB ×2', engineerId: 18 },
  // 계약 3 — 젠솔 OEM
  { id: 8, contractId: 3, customer: '한국수출입은행', solution: '규정관리 v1.8', target: '솔루션', serverSpec: 'Windows Server 2022 / 8core 32GB', engineerId: 26 },
  // 계약 4 — 종료
  { id: 9, contractId: 4, customer: '국세청', solution: '검색엔진 v3.7', target: '솔루션', serverSpec: 'RHEL7 / 8core 32GB', engineerId: 26 },
]

export const mockContacts: MaintenanceContact[] = [
  { id: 1, siteId: 1, kind: '계약사', name: '김도현', title: '과장', phone: '010-2100-0001', email: 'dhkim@gaonai.example' },
  { id: 2, siteId: 1, kind: '고객사', name: '박서진', title: '전산팀장', phone: '051-890-0002', email: 'sjpark@paik.example' },
  { id: 3, siteId: 6, kind: '고객사', name: '이현주', title: '차장', phone: '055-290-0003', email: 'hjlee@knb.example' },
  { id: 4, siteId: 7, kind: '고객사', name: '정민수', title: '부부장', phone: '02-2002-0004', email: 'msjung@wooribank.example' },
  { id: 5, siteId: 8, kind: '계약사', name: '최영란', title: '이사', phone: '02-555-0005', email: 'yrchoi@gensol.example' },
]

// 실측 재현: 열린 이슈 다수가 무배정 → 미배정 필터 검증. 기본 배정은 사이트 엔지니어.
export const mockIssues: MaintenanceIssue[] = [
  { id: 1, siteId: 6, type: '장애', title: '검색 결과 간헐적 타임아웃 (오전 피크)', status: '처리중', assigneeId: 18, receivedAt: '2026-08-04', completedAt: null, version: 1 },
  { id: 2, siteId: 1, type: '문의', title: '색인 스케줄 변경 방법 문의', status: '접수', assigneeId: 17, receivedAt: '2026-08-06', completedAt: null, version: 1 },
  { id: 3, siteId: 4, type: '장애', title: '서버 디스크 사용률 92% 경고', status: '접수', assigneeId: null, receivedAt: '2026-08-07', completedAt: null, version: 1 },
  { id: 4, siteId: 7, type: '요청', title: '신규 문서함 3종 색인 대상 추가 요청', status: '고객확인대기', assigneeId: 18, receivedAt: '2026-07-28', completedAt: null, version: 1 },
  { id: 5, siteId: 3, type: '문의', title: '검색 로그 월간 리포트 추출 요청', status: '완료', assigneeId: 19, receivedAt: '2026-07-15', completedAt: '2026-07-18', version: 1 },
  { id: 6, siteId: 2, type: '요청', title: '동의어 사전 일괄 등록 (200건)', status: '처리중', assigneeId: 17, receivedAt: '2026-08-01', completedAt: null, version: 1 },
  { id: 7, siteId: 8, type: '장애', title: '규정 개정 알림 미발송', status: '접수', assigneeId: null, receivedAt: '2026-08-08', completedAt: null, version: 1 },
  { id: 8, siteId: 5, type: '문의', title: '관리자 계정 추가 발급 절차', status: '완료', assigneeId: 20, receivedAt: '2026-06-20', completedAt: '2026-06-21', version: 1 },
]

export const mockComments: IssueComment[] = [
  { id: 1, issueId: 1, authorId: 18, content: '피크 시간대 스레드 덤프 확보. 색인 병행 시 GC 급증 확인 — 힙 증설 또는 색인 시간 이동 검토.', createdAt: '2026-08-05 10:12' },
  { id: 2, issueId: 1, authorId: 18, content: '고객사와 협의: 색인 스케줄 02시로 이동, 1주 모니터링.', createdAt: '2026-08-06 17:40' },
  { id: 3, issueId: 4, authorId: 18, content: '색인 대상 추가 완료, 고객 검수 요청 발송.', createdAt: '2026-08-02 14:05' },
  { id: 4, issueId: 5, authorId: 19, content: '리포트 전달 완료.', createdAt: '2026-07-18 09:30' },
]
