-- 통합 감사 로그(G1-3)의 최신순 조회 인덱스 — 2026-08-23 조회 뷰 구현분.
--
-- V3가 만든 것은 `(project_id, created_at desc)`와 `(entity_type, entity_id,
-- created_at desc)` 둘뿐이다. 선행 컬럼이 project_id·entity_type이므로 필터 없는
-- `order by created_at desc limit N`(통합 목록)에는 쓰이지 못하고 전체 정렬이 된다.
-- audit_logs는 모든 변경마다 한 행씩 늘어나는 테이블이라 그 정렬은 시간이 지나면
-- 관리자 화면 첫 페이지의 비용이 된다.
create index ix_audit_created on audit_logs (created_at desc);
