-- 진척률 100% 도달 시각 (2026-08-25) — AC F3-1의 "100%인 채 7일 경과"가 재는 기준점.
--
-- **왜 컬럼인가**: F3-1은 "100% 도달 시각 추적은 단순·표준 구현"을 명시적으로
-- 허용한다(`// ASSUMPTION:` 주석 요구). 대안이던 감사 로그 역산은 append-only
-- JSON diff를 스캔해 "언제 100이 됐고 그 뒤로 안 내려갔는가"를 재구성해야 하고,
-- `AuditQueryService`는 권한 판정이 없는 순수 조회라 그 용도로 넓힐 수 없다
-- (2026-08-22 결정 — 두 뷰가 다르게 판정하므로 audit은 판정을 가질 수 없다).
--
-- **null이 곧 "100%가 아니다"**: 별도 플래그를 두지 않는다. 진척률이 100 미만으로
-- 내려가면(수정·재개) 이 값이 비워지므로 F3-2의 "재개 후 다시 100% 도달하면 새
-- 사이클로 재계산"이 저절로 성립한다 — 시각이 새로 찍히기 때문이다.
--
-- **기존 행은 전부 null이다**: 시드 382건 중 진행중·100%인 행이 있어도 도달 시각을
-- 지어낼 수 없다(그 사실은 어디에도 없다). 그 프로젝트들은 다음에 진척률이
-- 갱신될 때 시각을 얻는다 — 없는 과거를 만들어 알림을 보내지 않는다.
alter table projects
    add column hundred_reached_at timestamp with time zone;

-- F3-1 스케줄러의 질의: 진행중 + 100% + 도달 시각이 기준일 이전.
-- 부분 인덱스인 이유는 대상이 언제나 극소수이기 때문이다(382건 중 몇 건).
create index ix_project_hundred_reached
    on projects (hundred_reached_at)
    where hundred_reached_at is not null;

-- F2-1 스케줄러의 질의: 진행중 + 종료일이 D-7 이내.
-- 종료일은 이미 조회에 쓰이지만 상태와 함께 거르는 인덱스는 없었다.
create index ix_project_status_end_date
    on projects (status, end_date)
    where deleted = false;
