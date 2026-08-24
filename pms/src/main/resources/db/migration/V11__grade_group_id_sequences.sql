-- 직급·권한 그룹 id 발급을 시퀀스로 (2026-08-24 — EPIC E 쓰기 착수).
--
-- 규칙은 이미 서 있었다(PRD-pms 부록 B, 유지보수 계약 id 결정): 명시 id 참조 데이터는
-- **하드 삭제가 없으면 `max(id)+1`, 있으면 시퀀스**다. 직급(E4-3)과 권한 그룹(E5-4)은
-- 사용 중이 아니면 하드 삭제되므로 조직 노드와 같은 칸이고, 그쪽에서는 `max(id)+1`이
-- 실제로 데이터를 오염시켰다 — 삭제된 MOIN개발팀(17)의 id를 새 노드가 받아 그 팀의
-- 비활성 인원 2명이 엉뚱한 조직에 붙었다(2026-08-22, V5·V6로 전환).
--
-- 같은 경로가 여기에도 있다: 인원은 soft 삭제라 비활성 인원의 `grade_id`·`group_id`가
-- 삭제된 행을 계속 가리키고, 감사 로그는 append-only라 지워진 id를 영원히 참조한다.
-- 시퀀스는 되돌아가지 않으므로 재사용이 원천적으로 없다.
create sequence if not exists grade_id_seq;
create sequence if not exists permission_group_id_seq;

-- V6의 교훈대로 **역대 최고값** 위에서 시작한다 — 살아 있는 행만 보면 삭제된 id를
-- 다시 내주게 된다. 빈 DB에서는 1이 되고, 이어서 시드가 명시 id로 적재한 뒤
-- `PersonSeedLoader`가 같은 기준으로 다시 올린다.
select setval('grade_id_seq', greatest(
        (select coalesce(max(id), 1) from grades),
        (select coalesce(max(grade_id), 1) from people),
        (select coalesce(max(entity_id), 1) from audit_logs where entity_type = 'Grade')));

select setval('permission_group_id_seq', greatest(
        (select coalesce(max(id), 1) from permission_groups),
        (select coalesce(max(group_id), 1) from people),
        (select coalesce(max(entity_id), 1) from audit_logs
          where entity_type = 'PermissionGroup')));
