-- (주)프로텐 실제 조직 — person 모듈 시드 (조직·직급·권한 그룹·인원).
--
-- 이 앱의 스키마(`org_units` · `grades` · `permission_groups` · `people`)에 맞춘
-- 형식이다. 기동 시 `PersonSeedLoader`가 people이 비어 있을 때만 실행한다
-- (`pms.seed.path` 미설정이면 비활성 — 테스트는 자체 픽스처를 쓴다).
-- 수동 적용: docker compose -f pms/docker-compose.yml exec -T postgres \
--              psql -U pms -d pms -v ON_ERROR_STOP=1 < reference/seed/seed_org_proten.sql
--
-- 원본(사내 조직 탭) → 이 스키마 변환 규칙:
--   * 직위(posNm) → grades, 직책(resNm) → permission_groups
--   * 원본의 `org_units.type{DEPARTMENT,TEAM}` 은 버린다 — "부문"·"팀"은 트리 상
--     위치의 파생 개념이고 별도 타입을 두지 않는다(PRD-pms §4 확정)
--   * 원본에 없던 **회사 root 노드(id 1)** 를 넣는다 — 부서가 root가 되면
--     부문 가시성(DIVISION scope = root 직계 자식의 subtree)이 팀 가시성으로
--     무너진다. 부록 B의 "시드 부문·팀 2단을 회사(root) 아래 적재"와 같다
--   * 원본 org_unit id N → 이 파일의 id N+1 (root가 1을 차지)
--   * 원본 user id N → person id N 그대로 유지 (SEQ = id)
--   * 원본 `users.email` → `users` 테이블의 로그인 ID (2026-08-21 인증 도입분).
--     초기 비밀번호는 전원 공용 해시이며 로그인은 pms.auth.enabled=true 일 때만 쓴다
--   * 원본 `users.is_admin` → 관리자 그룹 배정
--
-- 멱등: 재실행해도 안전하도록 전부 ON CONFLICT DO NOTHING. TRUNCATE 하지 않는다
-- (프로젝트·배정까지 지워 버린다 — 적재 여부는 로더의 빈 테이블 검사가 판단한다).

SET client_encoding TO 'UTF8';

BEGIN;

-- 조직: 회사(1) → 부문(3~7·18) → 팀(2·8~17) --------------------------------
-- 2026-08-24 재편(사용자 제공 조직도 기준): `관리•마케팅부`(18)를 신설하고 그 아래로
--   `경영관리팀`(2)을 내렸다. 구 시드는 둘을 한 노드로 합쳐 경영관리팀을 부문 자리에
--   두었는데, 실제 조직도는 부문(관리•마케팅부) → 팀(경영관리팀) 2단이다.
--   id는 보존한다(경영관리팀은 계속 2다) — 인원·감사 로그가 id로 그 노드를 가리킨다.
-- 대표는 **회사(root) 직속**이다(사용자 결정) — 조직도에 대표 부문이 없다.
--   root 직속 소속의 부문 파생값은 root 자신이다(`OrgTree.topDivisionIdOf`의 규약).
INSERT INTO org_units (id, parent_id, name, version) VALUES
  (1,  NULL, '프로텐',            0),
  (2,  18,   '경영관리팀',        0),
  (3,  1,    'AX사업기획부',      0),
  (4,  1,    'AI기술연구소',      0),
  (5,  1,    'AX솔루션사업부',    0),
  (6,  1,    'AX기술연구소',      0),
  (7,  1,    'MS사업부',          0),
  (8,  3,    'AX영업팀',          0),
  (9,  3,    'AX기획마케팅팀',    0),
  (10, 4,    'AI팀',              0),
  (11, 5,    'AX솔루션개발1팀',   0),
  (12, 5,    'AX솔루션개발2팀',   0),
  (13, 5,    'CS사업팀',          0),
  (14, 6,    'AX개발팀',          0),
  (15, 7,    'MS개발팀',          0),
  (16, 7,    'MS솔루션팀',        0),
  (17, 7,    'MOIN개발팀',        0),
  (18, 1,    '관리•마케팅부',     0)
ON CONFLICT (id) DO NOTHING;

-- 직급: 보정 가동률 계수는 부록 B가 원본 -------------------------------------
-- 원본 직위 코드 대응: CEO·VICE_PRESIDENT·EXEC_DIRECTOR·DIRECTOR·PRINCIPAL·
--   RESPONSIBLE·SENIOR·ASSISTANT → 8종의 인원 분포가 부록 B와 정확히 일치해
--   매핑이 데이터로 결정된다(주임 17·수석 7·이사 5·책임 3·상무 2 …).
-- 부록 B의 '수습'은 두지 않는다 — 구 목업 시드에만 있던 직급이고 실 조직에는
--   해당자가 없다(2026-08-21 사용자 확인).
-- 원본의 MANAGER는 남은 실제 직위이고, 실제 직위명은 **과장**이다(2026-08-24 사용자
--   확인 — 해당 인원 1명 김도한). 변환 당시 원본 코드 이름을 그대로 옮겨 '매니저'로
--   두었던 것을 실제 명부에 맞춘다. 계수는 그대로 1.0이다.
--   ASSUMPTION: 계수 1.0 — 부록 B에 대응 값이 없어 유일하게 유도할 수 없는
--   칸이다. 1.0은 가중이 없는 지점(선임과 동일)이라 보정 가동률을 어느 쪽으로도
--   왜곡하지 않고, 보정은 과부하 판정에 쓰지 않는 보조 지표다(상위 PRD §3).
--   실제 단가 위치가 다르면 이 한 행의 coeff만 바꾸면 된다.
-- VICE_PRESIDENT도 실제 직위명이 **부대표**다(2026-08-24 사용자 확인 — 해당 인원
--   1명 천용우). 계수 1.8은 그대로다.
-- id는 계수 순이 아니다 — 과장(구 MANAGER)이 나중에 들어와 9를 차지했다(인원 행 보존).
INSERT INTO grades (id, name, coeff, version) VALUES
  (1, '대표이사', 2.0, 0),
  (2, '부대표',   1.8, 0),
  (3, '상무',     1.7, 0),
  (4, '이사',     1.6, 0),
  (5, '수석',     1.5, 0),
  (6, '책임',     1.2, 0),
  (7, '선임',     1.0, 0),
  (8, '주임',     0.8, 0),
  (9, '과장',     1.0, 0)
ON CONFLICT (id) DO NOTHING;

-- 권한 그룹: 기본 4종 (부록 B 확정 — 상위 PRD §4-3이 규칙 원본) --------------
-- 원본 직책 대응: HEAD→부문장 · LEAD→팀장 · MEMBER→팀원 · is_admin→관리자
-- ASSUMPTION: 원본의 EXECUTIVE(대표·부대표) 중 is_admin이 아닌 부대표는 부문장에
--   넣는다. 기본 그룹은 4종 고정이고 "전사 가시성 + 관리 플래그 없음" 그룹이
--   없으므로, 관리자(플래그 4종 전부)를 주는 것보다 소속 부문 가시성이 안전하다.
INSERT INTO permission_groups
  (id, name, visibility_scope, create_project, manage_contracts,
   manage_all_projects, manage_org, system_fixed, version) VALUES
  (1, '관리자', 'COMPANY',  true,  true,  true,  true,  true,  0),
  (2, '부문장', 'DIVISION', true,  true,  false, false, false, 0),
  (3, '팀장',   'TEAM',     true,  true,  false, false, false, 0),
  -- 팀원 scope: SELF → TEAM (2026-08-22 결정 — 같은 팀 인원·프로젝트는 서로 보인다.
  --   구 SELF는 팀원이 팀 동료조차 조회할 수 없어 실무와 어긋났다. MCP find_person·
  --   가동률·eval 기대값이 같은 규칙을 따르므로 파급은 결정 기록 참조)
  (4, '팀원',   'TEAM',     false, false, false, false, false, 0)
ON CONFLICT (id) DO NOTHING;

-- 인원 43명 + 시스템 계정 1명 ------------------------------------------------
-- billable=false: 관리•마케팅부 subtree 3명 + 대표 1명(1~4) + AX사업기획부 subtree
--   전체(5~10) = 10명. 2026-08-24 재편으로 조직 노드가 갈렸지만 **대상 인원은 그대로다**
--   — 대표(1)는 root 직속이라 조직 단위로 표현할 수 없어 개인 플래그로 남는다.
--   부록 B의 "프로젝트 수행 0인 지원조직 3부문 10명" 규칙을 이 조직에 적용한 것이다.
-- capacity: 전원 1.0 (부록 B — 월별 가용 M/M은 Capacity 도입 시 세분화)
INSERT INTO people
  (id, name, org_unit_id, grade_id, group_id, capacity, billable,
   system_account, active, version) VALUES
  (1,  '박재완',              1,  1, 1, 1.0, false, false, true, 0),
  (2,  '천용우',             18,  2, 2, 1.0, false, false, true, 0),
  (3,  '서현정',              2,  5, 4, 1.0, false, false, true, 0),
  (4,  '진희원',              2,  7, 4, 1.0, false, false, true, 0),
  (5,  '주정호',              3,  4, 2, 1.0, false, false, true, 0),
  (6,  '김도한',              8,  9, 4, 1.0, false, false, true, 0),
  (7,  '윤종헌',              8,  8, 4, 1.0, false, false, true, 0),
  (8,  '장대근',              9,  5, 3, 1.0, false, false, true, 0),
  (9,  '마유림',              9,  7, 4, 1.0, false, false, true, 0),
  (10, '김주선',              9,  8, 4, 1.0, false, false, true, 0),
  (11, '강광선',              4,  3, 2, 1.0, true,  false, true, 0),
  (12, '임도환',             10,  5, 3, 1.0, true,  false, true, 0),
  (13, '김영삼',             10,  5, 4, 1.0, true,  false, true, 0),
  (14, '예고르 아르테미예프', 10,  8, 4, 1.0, true,  false, true, 0),
  (15, '이정준',             10,  8, 4, 1.0, true,  false, true, 0),
  (16, '김문수',              5,  4, 2, 1.0, true,  false, true, 0),
  (17, '이현창',             11,  5, 3, 1.0, true,  false, true, 0),
  (18, '김경민',             11,  7, 4, 1.0, true,  false, true, 0),
  (19, '고예림',             11,  8, 4, 1.0, true,  false, true, 0),
  (20, '김가은',             11,  8, 4, 1.0, true,  false, true, 0),
  (21, '추인식',             11,  8, 4, 1.0, true,  false, true, 0),
  (22, '김은채',             12,  7, 3, 1.0, true,  false, true, 0),
  (23, '허재원',             12,  8, 4, 1.0, true,  false, true, 0),
  (24, '정인우',             12,  8, 4, 1.0, true,  false, true, 0),
  (25, '배정빈',             12,  8, 4, 1.0, true,  false, true, 0),
  (26, '배성수',             13,  7, 3, 1.0, true,  false, true, 0),
  (27, '김민환',             13,  7, 4, 1.0, true,  false, true, 0),
  (28, '남진식',             13,  8, 4, 1.0, true,  false, true, 0),
  (29, '이은지',             13,  8, 4, 1.0, true,  false, true, 0),
  (30, '황희철',             15,  5, 3, 1.0, true,  false, true, 0),
  (31, '권예리',             15,  6, 4, 1.0, true,  false, true, 0),
  (32, '이원준',             15,  8, 4, 1.0, true,  false, true, 0),
  (33, '이승환',             16,  4, 3, 1.0, true,  false, true, 0),
  (34, '이응진',             16,  6, 4, 1.0, true,  false, true, 0),
  (35, '조규석',              7,  3, 2, 1.0, true,  false, true, 0),
  (36, '장용욱',             17,  4, 4, 1.0, true,  false, true, 0),
  (37, '장진영',             17,  5, 4, 1.0, true,  false, true, 0),
  (38, '김덕규',              6,  4, 2, 1.0, true,  false, true, 0),
  (39, '이상우',             14,  6, 3, 1.0, true,  false, true, 0),
  (40, '윤동환',             14,  8, 4, 1.0, true,  false, true, 0),
  (41, '권성은',             14,  8, 4, 1.0, true,  false, true, 0),
  (42, '홍영범',             14,  8, 4, 1.0, true,  false, true, 0),
  (43, '이혜린',             14,  8, 4, 1.0, true,  false, true, 0),
  -- 시스템 관리자 계정 (부록 B 확정) — 감사 actor·수습 주체를 개인(대표) 퇴사와
  -- 절연한다. system_account=true라 인력·가동률·배정 목록에서 제외된다.
  -- 로그인 email(admin@proten.co.kr)은 User 엔티티의 것이라 여기 없다.
  (44, '시스템관리자',        1,  1, 1, 0.0, false, true,  true, 0)
ON CONFLICT (id) DO NOTHING;

-- 로그인 계정 43 + 시스템 관리자 1 -------------------------------------------
-- 로그인 ID = 원본 사번 email. 초기 비밀번호는 부록 B 확정값 `proten1!`이고
-- 해시는 전원 공용이다(같은 평문이라 값이 같아도 무해 — 최초 로그인 후 변경 안내).
-- 해시 재생성: cd pms && ./gradlew printPasswordHash
INSERT INTO users (id, person_id, email, password_hash, phone, version) VALUES
  (1,  1,  'pro0001@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (2,  2,  'pro0007@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (3,  3,  '20210005@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (4,  4,  '20260006@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (5,  5,  '20260001@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (6,  6,  'pro0019@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (7,  7,  '20260004@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (8,  8,  '20220011@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (9,  9,  '20220009@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (10, 10, '20250002@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (11, 11, 'pro0004@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (12, 12, '20210011@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (13, 13, 'pro0014@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (14, 14, '20260002@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (15, 15, '20260003@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (16, 16, 'pro0006@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (17, 17, 'pro0016@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (18, 18, 'pro0017@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (19, 19, '20240008@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (20, 20, '20250001@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (21, 21, '20250006@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (22, 22, 'pro0009@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (23, 23, '20240007@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (24, 24, '20250005@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (25, 25, '20250013@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (26, 26, '20210007@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (27, 27, '20220005@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (28, 28, '20230008@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (29, 29, '20240006@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (30, 30, '20210004@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (31, 31, '20220010@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (32, 32, '20240005@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (33, 33, '20240009@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (34, 34, '20210008@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (35, 35, 'pro0002@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (36, 36, 'pro0003@proten.co.kr',  '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (37, 37, '20220007@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (38, 38, '20220008@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (39, 39, '20210009@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (40, 40, '20230007@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (41, 41, '20240001@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (42, 42, '20240002@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (43, 43, '20250003@proten.co.kr', '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0),
  (44, 44, 'admin@proten.co.kr',    '$2a$10$HE8dvefDMNrmwNJ8ugmwCOuTkUoAD4xpeMAQqiD/dKA2C7xjeLaRe', NULL, 0)
ON CONFLICT (id) DO NOTHING;

COMMIT;
