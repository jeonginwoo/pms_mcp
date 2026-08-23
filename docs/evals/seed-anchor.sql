-- eval 코퍼스 시드 앵커 재현 질의 (seed-anchor-map.md §4~6의 원천)
--
-- 실행: docker compose -f pms/docker-compose.yml exec -T postgres \
--         psql -U pms -d pms -f - < docs/evals/seed-anchor.sql
--
-- 전제: 빈 DB에서 `pms` 앱을 한 번 기동해 시드 로더 3종이 적재를 마친 상태
--       (인원 44 · 프로젝트 382/배정 461 · 유지보수 105/157/14).
--
-- 산식·모집단 규칙의 원본은 상위 PRD §3이고, "진행중 배정만" 규칙은
-- project.MonthlyAssignment javadoc이 명시한다. 여기서는 그 규칙을 SQL로
-- 옮겨 두어, EPIC C 구현 전에도 기대값을 실 데이터로 확인할 수 있게 한다.
-- EPIC C가 서면 이 파일의 수치는 `get_utilization` 응답과 일치해야 한다.

\pset border 2

-- ── 뷰·함수 (재실행 안전) ────────────────────────────────────────────────
-- REPLACE가 아니라 DROP부터 하는 이유: CREATE OR REPLACE VIEW는 컬럼 순서·이름을
-- 바꾸지 못해, 이 파일을 고친 뒤 재실행하면 "cannot change name of view column"으로 막힌다.

DROP VIEW IF EXISTS v_visible_projects;
DROP VIEW IF EXISTS v_visible_people;
DROP VIEW IF EXISTS v_person;
DROP VIEW IF EXISTS v_subtree;
DROP FUNCTION IF EXISTS f_util(text);

-- 조직 subtree 전개 — 가시성 scope 해석에 쓴다(TeamScopeResolver·DivisionScopeResolver)
CREATE VIEW v_subtree AS
WITH RECURSIVE t(root, node) AS (
    SELECT id, id FROM org_units
    UNION ALL
    SELECT t.root, o.id FROM org_units o JOIN t ON o.parent_id = t.node)
SELECT root, node FROM t;

-- 인원 + 조직·직급·그룹 평면화. 부문 = root(1) 직계 자식까지 올라간 노드
CREATE VIEW v_person AS
SELECT pe.id, pe.name, pe.capacity, pe.billable,
       g.name AS grade, g.coeff, pg.name AS grp, pg.visibility_scope,
       ou.id AS team_id, ou.name AS team,
       COALESCE(pou.id, ou.id) AS div_id, COALESCE(pou.name, ou.name) AS division
  FROM people pe
  JOIN org_units ou ON ou.id = pe.org_unit_id
  LEFT JOIN org_units pou ON pou.id = ou.parent_id AND pou.parent_id IS NOT NULL
  JOIN grades g ON g.id = pe.grade_id
  JOIN permission_groups pg ON pg.id = pe.group_id
 WHERE NOT pe.system_account;

-- 그 달의 배정 M/M 합 — 모집단은 진행중 프로젝트뿐.
-- 겹침 판정 하나로 "종료월 이후 제외"가 성립한다(종료 시 endDate가 종료월 말일로 당겨짐, AC B2-1)
CREATE FUNCTION f_util(ym text) RETURNS TABLE(person_id bigint, mm double precision) AS $$
  SELECT a.person_id, SUM(a.monthly_mm)
    FROM project_assignments a
    JOIN projects p ON p.id = a.project_id AND NOT p.deleted AND p.status = 'IN_PROGRESS'
   WHERE a.status = 'ACTIVE'
     AND a.start_date <= (ym || '-01')::date + INTERVAL '1 month' - INTERVAL '1 day'
     AND a.end_date   >= (ym || '-01')::date
   GROUP BY 1;
$$ LANGUAGE sql;

-- 화자별 가시 인원 (OrgVisibilityServiceImpl + scope 해석자 4종)
CREATE VIEW v_visible_people AS
SELECT c.id AS caller, pe.id AS visible
  FROM people c JOIN permission_groups pg ON pg.id = c.group_id
  JOIN people pe ON pe.active
 WHERE pg.visibility_scope = 'COMPANY'
UNION
SELECT c.id, pe.id
  FROM people c JOIN permission_groups pg ON pg.id = c.group_id
  JOIN org_units co ON co.id = c.org_unit_id
  JOIN org_units dv ON dv.id = CASE WHEN co.parent_id = 1 THEN co.id ELSE co.parent_id END
  JOIN v_subtree st ON st.root = dv.id
  JOIN people pe ON pe.org_unit_id = st.node AND pe.active
 WHERE pg.visibility_scope = 'DIVISION'
UNION
SELECT c.id, pe.id
  FROM people c JOIN permission_groups pg ON pg.id = c.group_id
  JOIN v_subtree st ON st.root = c.org_unit_id
  JOIN people pe ON pe.org_unit_id = st.node AND pe.active
 WHERE pg.visibility_scope = 'TEAM'
UNION
SELECT c.id, c.id FROM people c;  -- 본인은 항상 포함 (OrgVisibility.of)

-- 가시 프로젝트 = 가시 인원이 한 명이라도 배정된 프로젝트 (ProjectVisibilityService)
CREATE VIEW v_visible_projects AS
SELECT DISTINCT vp.caller, a.project_id
  FROM v_visible_people vp
  JOIN project_assignments a ON a.person_id = vp.visible AND a.status = 'ACTIVE'
  JOIN projects p ON p.id = a.project_id AND NOT p.deleted;


\echo '=== §4-1 개인 가동률 (0% 초과 · 기본/보정) ==='
SELECT m.ym, v.id, v.name, v.grp, v.coeff, v.team, v.division, v.billable,
       ROUND((u.mm / v.capacity * 100)::numeric, 0)            AS basic,
       ROUND((u.mm * v.coeff / v.capacity * 100)::numeric, 1)  AS adjusted
  FROM (VALUES ('2026-07'), ('2026-08'), ('2026-09')) m(ym)
  JOIN v_person v ON true
  JOIN LATERAL f_util(m.ym) u ON u.person_id = v.id
 ORDER BY m.ym, basic DESC;

\echo '=== §4-2 과부하 명단 (billable=true · 기본 > 100) ==='
SELECT m.ym, v.name, v.team, v.division, ROUND((u.mm / v.capacity * 100)::numeric, 0) AS basic
  FROM (VALUES ('2026-07'), ('2026-08'), ('2026-09')) m(ym)
  JOIN v_person v ON v.billable
  JOIN LATERAL f_util(m.ym) u ON u.person_id = v.id
 WHERE u.mm / v.capacity * 100 > 100
 ORDER BY m.ym, basic DESC;

\echo '=== §4-3 조직 집계 (billable=true) ==='
SELECT ym, division, COUNT(*) AS n, ROUND(AVG(basic)::numeric, 1) AS avg_basic,
       COUNT(*) FILTER (WHERE basic > 100) AS overbooked
  FROM (SELECT m.ym, v.division, COALESCE(u.mm, 0) / v.capacity * 100 AS basic
          FROM (VALUES ('2026-07'), ('2026-08'), ('2026-09')) m(ym)
          JOIN v_person v ON v.billable
          LEFT JOIN LATERAL f_util(m.ym) u ON u.person_id = v.id) t
 GROUP BY ROLLUP(ym, division) HAVING ym IS NOT NULL ORDER BY ym, division NULLS FIRST;

\echo '=== §4-4 팀 구성 (2026-08 · billable=true) ==='
SELECT v.team, COUNT(*) AS n, ROUND(AVG(COALESCE(u.mm, 0) / v.capacity * 100)::numeric, 1) AS avg_basic,
       STRING_AGG(v.name || ' ' || ROUND((COALESCE(u.mm, 0) / v.capacity * 100)::numeric, 0) || '%',
                  ', ' ORDER BY COALESCE(u.mm, 0) DESC) AS members
  FROM v_person v LEFT JOIN LATERAL f_util('2026-08') u ON u.person_id = v.id
 WHERE v.billable GROUP BY 1 ORDER BY 3 DESC;

\echo '=== §5 화자별 가시 프로젝트 (상태별) ==='
SELECT v.id, v.name, v.grp, v.visibility_scope AS scope, v.team,
       COUNT(p.id) FILTER (WHERE p.status = 'IN_PROGRESS')      AS 진행중,
       COUNT(p.id) FILTER (WHERE p.status = 'ORDER_CONFIRMED')  AS 수주확정,
       COUNT(p.id) FILTER (WHERE p.status = 'CONTRACT_PENDING') AS 계약대기,
       COUNT(p.id) FILTER (WHERE p.status = 'COMPLETED')        AS 완료,
       COUNT(p.id)                                              AS 총
  FROM v_person v
  LEFT JOIN v_visible_projects vis ON vis.caller = v.id
  LEFT JOIN projects p ON p.id = vis.project_id
 GROUP BY 1, 2, 3, 4, 5 ORDER BY 10 DESC;

\echo '=== §5 케이스 앵커 프로젝트 상세 ==='
SELECT p.id, p.client, p.name, p.solution, p.status, p.progress, p.version,
       p.contract_mm, p.start_date, p.end_date, pm.name AS pm
  FROM projects p JOIN people pm ON pm.id = p.manager_id
 WHERE p.id IN (317, 322, 332, 334, 340, 344, 347, 351, 355) ORDER BY p.id;

\echo '=== §5 앵커 프로젝트 배정 ==='
SELECT a.project_id, pe.name, ou.name AS team, a.role, a.monthly_mm, a.start_date, a.end_date
  FROM project_assignments a
  JOIN people pe ON pe.id = a.person_id
  JOIN org_units ou ON ou.id = pe.org_unit_id
 WHERE a.project_id IN (317, 332, 340, 344, 347) ORDER BY 1, 4, 2;

\echo '=== §5 keyword 검색 (부분 일치 — 도구 description과 같은 범위) ==='
SELECT kw AS keyword, p.status, p.id, p.name,
       BOOL_OR(vis.caller = 1)  AS "박재완(전사)",
       BOOL_OR(vis.caller = 16) AS "김문수(부문장)",
       BOOL_OR(vis.caller = 17) AS "이현창(팀장)"
  FROM (VALUES ('ai 검색'), ('한국거래소'), ('ms사업부')) k(kw)
  JOIN projects p ON NOT p.deleted AND p.status = 'IN_PROGRESS'
                 AND (lower(p.name) LIKE '%' || k.kw || '%'
                   OR lower(p.client) LIKE '%' || k.kw || '%'
                   OR lower(p.solution) LIKE '%' || k.kw || '%')
  LEFT JOIN v_visible_projects vis ON vis.project_id = p.id
 GROUP BY 1, 2, 3, 4 ORDER BY 1, 3;

\echo '=== §5 키스톤 — PM의 권한 그룹 분포 ==='
SELECT pg.name AS pm_group, COUNT(*) AS 전체,
       COUNT(*) FILTER (WHERE p.status = 'IN_PROGRESS') AS 진행중
  FROM projects p JOIN people pm ON pm.id = p.manager_id
  JOIN permission_groups pg ON pg.id = pm.group_id
 WHERE NOT p.deleted GROUP BY 1 ORDER BY 2 DESC;

\echo '=== §6 유지보수 계약 101 + 이슈 ==='
SELECT c.id, c.name, c.contractor, STRING_AGG(DISTINCT s.name, ', ') AS sites
  FROM maintenance_contracts c LEFT JOIN maintenance_sites s ON s.contract_id = c.id
 WHERE c.id = 101 GROUP BY 1, 2, 3;
SELECT i.id, i.type, i.received_at, i.title
  FROM maintenance_issues i JOIN maintenance_sites s ON s.id = i.site_id
 WHERE s.contract_id = 101 ORDER BY i.type, i.received_at DESC;

\echo '=== §8-1 billable=false 인원의 배정 보유 (시드 정합 이슈) ==='
SELECT v.id, v.name, v.team, COUNT(*) AS 배정,
       ROUND(SUM(a.monthly_mm)::numeric, 2) AS mm합,
       STRING_AGG(DISTINCT p.status, ',') AS 상태
  FROM v_person v
  JOIN project_assignments a ON a.person_id = v.id AND a.status = 'ACTIVE'
  JOIN projects p ON p.id = a.project_id AND NOT p.deleted
 WHERE NOT v.billable GROUP BY 1, 2, 3 ORDER BY 4 DESC;

\echo '=== §8-2 과부하가 2개 이상 부문에 걸치는 달 (0건 = A-02 판별 불가의 근거) ==='
WITH months AS (
    SELECT to_char(d, 'YYYY-MM') AS ym
      FROM generate_series('2025-01-01'::date, '2027-09-01'::date, '1 month') d)
SELECT m.ym, COUNT(*) AS overbooked, COUNT(DISTINCT v.division) AS divisions
  FROM months m JOIN v_person v ON v.billable
  JOIN LATERAL f_util(m.ym) u ON u.person_id = v.id
 WHERE u.mm / v.capacity * 100 > 100
 GROUP BY 1 HAVING COUNT(DISTINCT v.division) > 1 ORDER BY 1;

\echo '=== F류 공백 월 확인 (관통 배정 0건) ==='
SELECT m.ym, COUNT(u.person_id) AS 배정_보유_인원
  FROM (VALUES ('2027-10'), ('2027-12'), ('2015-01'), ('2019-11')) m(ym)
  LEFT JOIN LATERAL f_util(m.ym) u ON true
 GROUP BY 1 ORDER BY 1;
