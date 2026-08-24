-- 조직 트리를 실제 조직도에 맞춘다 (2026-08-24 — 사용자 제공 조직도 기준).
--
-- 무엇이 틀렸나: 구 시드는 `관리•마케팅부`(부문)와 `경영관리팀`(팀)을 **한 노드로 합쳐**
-- 경영관리팀을 회사 직속(= 부문 자리)에 두었고, 대표도 그 팀에 넣었다. 실제 조직도는
-- 부문(관리•마케팅부) → 팀(경영관리팀) 2단이고 대표는 회사 직속이다. `AI개발팀`은
-- 실제 이름이 `AI팀`이며(시드 `projects.json`도 team="AI팀"을 쓰고 있었다), 회사 노드의
-- 표기는 `프로텐`이다.
--
-- 왜 여기서 수렴시키나: 인원 시드 로더는 조직 노드가 이미 있으면 적재 전체를 건너뛴다
-- (`ON CONFLICT DO NOTHING`도 parent_id·name을 고치지 않는다). 참조 데이터의 '정정'을
-- 마이그레이션으로 수렴시키는 것은 V4·V10·V13의 선례다. 빈 DB에서는 대상 행이 없어
-- 아무 일도 하지 않고, 이어서 시드가 교정된 구조로 넣는다.
--
-- 노드 id를 보존한다: 경영관리팀은 계속 2다. 인원의 `org_unit_id`와 감사 로그의
-- `entity_id`가 그 노드를 id로 가리키므로, 새 노드를 만들고 옮기면 과거 기록이 지워진
-- 노드를 가리킨다(V5·V6이 막으려 했던 바로 그 상황이다).

-- 1) 관리•마케팅부 신설 — **재편 전 구조가 실재할 때만** 만든다.
--    id는 시퀀스에서 받는다: 이 마이그레이션이 도는 DB의 18번이 비어 있다고 단정할
--    수 없다(조직 노드는 화면에서 신설·삭제된다 — E3-1·E3-3).
--
--    `exists(경영관리팀 parent_id = 1)` 조건이 핵심이다(2026-08-24 실측으로 추가):
--    빈 DB에서도 넣으면 시드가 적재되기 전에 노드가 하나 생기고, 그러면 ①시드가
--    같은 이름의 노드를 다시 넣어 **관리•마케팅부가 두 개**가 되고 ②노드가 이미
--    있다고 판단하는 로더 경로를 흔든다. 통합 테스트가 이것을 잡았다.
insert into org_units (id, parent_id, name, version)
select nextval('org_unit_id_seq'), 1, '관리•마케팅부', 0
where exists (select 1 from org_units where name = '경영관리팀' and parent_id = 1)
  and not exists (select 1 from org_units where name = '관리•마케팅부');

-- 2) 경영관리팀을 그 아래로 — 회사 직속이던 것만 옮긴다(이미 옮겨졌으면 조건이 걸러낸다).
update org_units
set parent_id = (select id from org_units where name = '관리•마케팅부' order by id limit 1)
where name = '경영관리팀' and parent_id = 1;

-- 3) 이름 정정 — 회사 표기와 AI팀.
update org_units set name = '프로텐' where parent_id is null and name = '(주)프로텐';
update org_units set name = 'AI팀' where name = 'AI개발팀';

-- 4) 인원 재배속 — 대표는 회사 직속, 부대표는 관리•마케팅부 직속(사용자 결정).
--    사람은 id로 짚는다: 인원 id는 시드 정본이고 soft 삭제라 재사용되지 않는다.
--    `and org_unit_id = 2`는 재편 전 상태에서만 실행되게 하는 조건이다.
update people set org_unit_id = 1 where id = 1 and org_unit_id = 2;
update people
set org_unit_id = (select id from org_units where name = '관리•마케팅부' order by id limit 1)
where id = 2 and org_unit_id = 2;

-- 5) 시퀀스를 역대 최고값 위로 (V6와 같은 이유 — 새 노드가 지워진 id를 받지 않게).
select setval('org_unit_id_seq', greatest(
        (select coalesce(max(id), 1) from org_units),
        (select coalesce(max(org_unit_id), 1) from people),
        (select coalesce(max(entity_id), 1) from audit_logs where entity_type = 'OrgUnit')));
