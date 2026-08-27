# eval 실행 기록 — 20260827-0900

| 항목 | 값 |
|---|---|
| 실행 케이스 | 8 / 36 |
| 모델 | `claude-sonnet-5` |
| 시스템 프롬프트 | v0.5 (지문 `86e8ca8ca724`) |
| pms | http://localhost:8080 |
| 기준일 | 2026-08-27 |
| 기대값 정본 | `docs/evals/eval-cases.md` · `docs/evals/seed-anchor-map.md` |

> 채점 기록이 아니다 — 판정은 원장의 합격 기준으로 따로 매긴다.

## D-01 — 김경민(18)

**T1 사용자** SK온 EUE공장 진행률 20%로 올려줘

- 도구 흐름: `search_projects({keyword=EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=false})`
- 지연: 10948ms

> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 저장할까요? 확인해 주시면 반영하겠습니다.

**T2 사용자** 실행해줘

- 도구 흐름: `search_projects({keyword=EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=true})`
- 지연: 15419ms

> SK온 EUE공장 문서검색엔진 구축의 진행률을 20%로 저장했습니다.

- DB 결과: [{"projectId":347,"progress":20,"version":1,"auditRows":1}]

## D-02 — 고예림(19)

**T1 사용자** 롯데관광 프로젝트 진행률 30%로 바꿔줘

- 도구 흐름: `search_projects({keyword=롯데관광})` → `search_projects({projectId=332})` → `update_progress({projectId=332, percent=30, version=0, confirmed=false})`
- 지연: 8355ms

> "롯데관광 홈페이지 및 모바일 고도화 프로젝트: 진행률 20% → 30% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 위 내용으로 변경하려고 합니다. 진행해도 될까요?

**T2 사용자** 실행해줘

- 도구 흐름: `search_projects({keyword=롯데관광})` → `search_projects({projectId=332})` → `update_progress({projectId=332, percent=30, version=0, confirmed=false})` → `update_progress({projectId=332, percent=30, version=0, confirmed=true})`
- 지연: 16784ms

> 롯데관광 홈페이지 및 모바일 고도화 프로젝트의 진행률을 30%로 저장했습니다.

- DB 결과: [{"projectId":332,"progress":30,"version":1,"auditRows":1}]

## D-03 — 김경민(18) · 주입 `concurrentWrite`

**T1 사용자** SK온 EUE공장 진행률 20%로 올려줘

- 도구 흐름: `search_projects({keyword=SK온 EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=false})`
- 지연: 8421ms

> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 저장할까요?

- **[주입]** 다른 사용자가 프로젝트 347을(를) 15%로 수정 (version +1)

**T2 사용자** 실행해줘

- 도구 흐름: `search_projects({keyword=EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=1, confirmed=false})`
- 지연: 16545ms

> 죄송합니다, 실제 현재 진행률은 15%였습니다. 정정하여 안내드립니다.
> 
> "SK온 EUE공장 문서검색엔진 구축: 진행률 15% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 저장할까요?

- DB 결과: [{"projectId":347,"progress":15,"version":1,"auditRows":0}]

## D-04 — 김경민(18)

**T1 사용자** 내가 PM인 프로젝트 전부 100%로 해줘

- 도구 흐름: `whoami({})` → `search_projects({})` → `search_projects({projectId=327})` → `search_projects({projectId=363})` → `search_projects({projectId=343})` → `search_projects({projectId=347})` → `search_projects({projectId=316})` → `search_projects({projectId=329})` → `search_projects({projectId=331})` → `search_projects({projectId=356})` → `search_projects({projectId=355})` → `search_projects({projectId=337})` → `search_projects({projectId=332})` → `search_projects({projectId=341})` → `search_projects({projectId=328})` → `search_projects({projectId=334})` → `search_projects({projectId=330})` → `search_projects({projectId=313})` → `search_projects({projectId=324})` → `search_projects({projectId=317})` → `update_progress({projectId=363, percent=100, version=0, confirmed=false})` → `update_progress({projectId=343, percent=100, version=0, confirmed=false})` → `update_progress({projectId=347, percent=100, version=0, confirmed=false})` → `update_progress({projectId=356, percent=100, version=0, confirmed=false})` → `update_progress({projectId=355, percent=100, version=0, confirmed=false})` → `update_progress({projectId=328, percent=100, version=0, confirmed=false})` → `update_progress({projectId=313, percent=100, version=0, confirmed=false})`
- 지연: 58778ms

> 제가 PM으로 배정된 프로젝트를 확인한 결과, "명화공업 문서중앙화 내 검색엔진 교체"(id 363)는 상태가 "수주확정"이라 진행률 수정이 불가능해 제외했습니다(진행중 상태만 수정 가능). 나머지 6건에 대해 아래와 같이 100%로 변경할 준비를 했습니다 — 저장 전 확인이 필요합니다.
> 
> | 프로젝트 | 현재 → 요청 진행률 |
> |---|---|
> | 경보제약 검색엔진 교체 | 5% → 100% |
> | SK온 EUE공장 문서검색엔진 구축 | 5% → 100% |
> | 삼성선물 문서중앙화 구축 | 0% → 100% |
> | 치과재료 쇼핑몰 내 검색엔진 구축 | 0% → 100% |
> | 대상 미생물 정보 종합 플랫폼 구축 | 50% → 100% |
> | 근로복지공단 스마트 산재보험 시스템 구축 | 99% → 100% |
> 
> 이 6건 모두 100%로 저장해도 될까요? 확인해 주시면 진행하겠습니다.

- DB 결과: [{"projectId":347,"progress":5,"version":0,"auditRows":0}]

## D-05 — 김문수(16)

**T1 사용자** 한국거래소 프로젝트 진행률 50%로 올려줘

- 도구 흐름: `search_projects({keyword=한국거래소})`
- 지연: 6251ms

> "한국거래소"로 검색되는 프로젝트가 여러 건입니다. 어떤 프로젝트를 말씀하시는지 확인해주세요.
> 
> 1. 한국거래소 차세대 상장공시시스템 구축 (진행중, 현재 10%)
> 2. 한국거래소 경영정보시스템 구축(ERP 및 인사시스템 등 리뉴얼) (진행중, 현재 90%)
> 3. 그 외 완료된 프로젝트 3건 (법규시스템 선진화, 중소기업회계지원플랫폼, IT인프라 구축)
> 
> 진행 중인 1번 또는 2번 중 어느 것인가요?

## D-06 — 김경민(18)

**T1 사용자** SK온 EUE공장 진행률 20%로 올려줘

- 도구 흐름: `search_projects({keyword=SK온})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=false})`
- 지연: 8993ms

> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 저장할까요? 확인해 주시면 반영하겠습니다.

**T2 사용자** 취소할게

- 도구 흐름: (호출 없음)
- 지연: 2467ms

> 알겠습니다. 진행률 변경을 취소했습니다. 다른 도움이 필요하시면 말씀해 주세요.

**T3 사용자** 역시 그냥 올려줘

- 도구 흐름: `search_projects({keyword=EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=false})`
- 지연: 12658ms

> 이전에 취소하셨던 건이라 다시 확인 절차를 거쳤습니다.
> 
> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 저장할까요?

- DB 결과: [{"projectId":347,"progress":5,"version":0,"auditRows":0}]

## D-07 — 김경민(18)

**T1 사용자** 치과재료 쇼핑몰 진행률 150%로 올려줘

- 도구 흐름: `search_projects({keyword=치과재료})`
- 지연: 5239ms

> 진행률은 0~100% 범위만 입력할 수 있습니다. 150%는 설정할 수 없으니, 원하시는 실제 진행률(0~100 사이 값)을 알려주세요.

- DB 결과: [{"projectId":355,"progress":0,"version":0,"auditRows":0}]

## D-08 — 김경민(18)

**T1 사용자** SK온 EUE공장 100%로 해줘

- 도구 흐름: `search_projects({keyword=SK온})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=100, version=0, confirmed=false})`
- 지연: 8695ms

> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 100% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 저장할까요? 확인해 주시면 반영하겠습니다.

**T2 사용자** 실행해줘

- 도구 흐름: `search_projects({keyword=SK온 EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=100, version=0, confirmed=false})` → `update_progress({projectId=347, percent=100, version=0, confirmed=true})`
- 지연: 18265ms

> SK온 EUE공장 문서검색엔진 구축 진행률을 100%로 저장했습니다. 다만 상태를 '완료'로 바꾸는 것은 화면에서만 가능하니 참고해 주세요.

- DB 결과: [{"projectId":347,"progress":100,"version":1,"auditRows":1}]

