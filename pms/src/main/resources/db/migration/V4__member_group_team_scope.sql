-- 팀원 기본 그룹의 가시성 scope: SELF → TEAM (2026-08-22 결정).
--
-- 시드(`reference/seed/seed_org_proten.sql`)도 함께 고쳤지만, 시드는 전부
-- ON CONFLICT DO NOTHING이라 **이미 적재된 DB는 값을 갱신받지 못한다** — 그래서
-- 참조 데이터의 '정정'은 마이그레이션으로 수렴시킨다(스키마 소유는 그대로 Flyway).
-- 빈 DB에서는 대상 행이 없어 아무 일도 하지 않고, 이어서 시드가 TEAM으로 넣는다.
update permission_groups
   set visibility_scope = 'TEAM'
 where id = 4
   and name = '팀원'
   and visibility_scope = 'SELF';
