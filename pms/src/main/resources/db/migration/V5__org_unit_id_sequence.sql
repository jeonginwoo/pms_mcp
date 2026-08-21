-- 조직 노드 id 발급을 시퀀스로 (2026-08-22 — 실기동에서 드러난 id 재사용 결함).
--
-- 조직 노드는 유일하게 **하드 삭제**되는 참조 데이터라(프로젝트·인원은 soft 삭제),
-- `max(id)+1` 방식이 삭제된 노드의 id를 그대로 다시 내준다. 그러면 그 노드를 가리키던
-- 비활성 인원이 **엉뚱한 새 조직 소속으로 보인다** — 실제로 MOIN개발팀(17)이 삭제된 뒤
-- 새 노드가 17을 받아 그 팀의 인원 2명이 새 노드에 붙었다.
-- 시퀀스는 되돌아가지 않으므로 재사용이 원천적으로 없다.
create sequence if not exists org_unit_id_seq;

-- 이미 적재된 DB는 현재 최대값에 맞춘다. 빈 DB에서는 1이 되고, 이어서 시드가
-- 명시 id(1~17)로 적재한 뒤 `PersonSeedLoader`가 시퀀스를 그 최대값으로 올린다.
select setval('org_unit_id_seq', greatest((select coalesce(max(id), 1) from org_units), 1));
