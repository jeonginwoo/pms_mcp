-- 조직 id 시퀀스를 **역대 최고값** 위로 올린다 (2026-08-22 — V5의 후속 교정).
--
-- V5는 살아 있는 노드의 최대값만 봤다. 그런데 삭제된 노드의 id를 아직 가리키는 행이
-- 있다: 비활성 인원의 `org_unit_id`가 그렇다(인원은 soft 삭제라 참조가 남는다).
-- 그 id를 새 노드에 다시 내주면 비활성 인원이 엉뚱한 팀 소속으로 되살아난다.
-- 그래서 살아 있는 노드 · 인원이 가리키는 노드 · 감사 로그에 남은 노드 id 셋 중
-- 가장 큰 값 위에서 시작한다(감사 로그는 append-only라 삭제 이력이 전부 남아 있다).
select setval('org_unit_id_seq', greatest(
        (select coalesce(max(id), 1) from org_units),
        (select coalesce(max(org_unit_id), 1) from people),
        (select coalesce(max(entity_id), 1) from audit_logs where entity_type = 'OrgUnit')));
