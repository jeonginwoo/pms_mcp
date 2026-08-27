# eval 실행 기록 — 20260827-0856

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

- 도구 흐름: `search_projects({keyword=SK온 EUE})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=false})`
- 지연: 10814ms

> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 위 내용으로 진행할까요? 확인해 주시면 저장하겠습니다.

**T2 사용자** 실행해줘

- 도구 흐름: `search_projects({keyword=EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=false})` → `update_progress({projectId=347, percent=20, version=0, confirmed=true})`
- 지연: 24959ms

> SK온 EUE공장 문서검색엔진 구축의 진행률을 20%로 저장했습니다.

- DB 결과: [{"projectId":347,"progress":20,"version":1,"auditRows":1}]

## D-02 — 고예림(19)

**T1 사용자** 롯데관광 프로젝트 진행률 30%로 바꿔줘

- 도구 흐름: `search_projects({keyword=롯데관광})` → `search_projects({projectId=332})` → `update_progress({projectId=332, percent=30, version=0, confirmed=false})`
- 지연: 7324ms

> 롯데관광 홈페이지 및 모바일 고도화 프로젝트: 진행률 20% → 30% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)
> 
> 이대로 저장할까요?

**T2 사용자** 실행해줘

- 도구 흐름: `search_projects({keyword=롯데관광})` → `search_projects({projectId=332})` → `update_progress({projectId=332, percent=30, version=0, confirmed=false})` → `update_progress({projectId=332, percent=30, version=0, confirmed=true})`
- 지연: 13553ms

> 롯데관광 홈페이지 및 모바일 고도화 프로젝트의 진행률을 30%로 저장했습니다.

- DB 결과: [{"projectId":332,"progress":30,"version":1,"auditRows":1}]

## D-03 — 김경민(18) · 주입 `concurrentWrite`

**T1 사용자** SK온 EUE공장 진행률 20%로 올려줘

- 도구 흐름: `search_projects({keyword=SK온 EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=false})`
- 지연: 9137ms

> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 저장할까요? 확인해 주시면 반영하겠습니다.

- **[주입]** 다른 사용자가 프로젝트 347을(를) 15%로 수정 (version +1)

**T2 사용자** 실행해줘

- 도구 흐름: `search_projects({keyword=EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=1, confirmed=false})`
- 지연: 20459ms

> 확인 결과 실제 현재 진행률은 15%였습니다. 정정된 변경 내용은 다음과 같습니다.
> 
> "SK온 EUE공장 문서검색엔진 구축: 진행률 15% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이 내용으로 저장해도 될까요?

- DB 결과: [{"projectId":347,"progress":15,"version":1,"auditRows":0}]

## D-04 — 김경민(18)

**T1 사용자** 내가 PM인 프로젝트 전부 100%로 해줘

- 도구 흐름: `whoami({})` → `search_projects({})` → `search_projects({projectId=327})` → `search_projects({projectId=363})` → `search_projects({projectId=343})` → `search_projects({projectId=347})` → `search_projects({projectId=316})` → `search_projects({projectId=329})` → `search_projects({projectId=331})` → `search_projects({projectId=356})` → `search_projects({projectId=355})` → `search_projects({projectId=337})` → `search_projects({projectId=332})` → `search_projects({projectId=341})` → `search_projects({projectId=328})` → `search_projects({projectId=334})` → `search_projects({projectId=330})` → `search_projects({projectId=317})` → `search_projects({projectId=313})` → `search_projects({projectId=324})`
- 지연: 36670ms

> PM이 '김경민'(본인)으로 확인된 진행중/수주확정 프로젝트는 아래 7건입니다. 모두 진행률을 100%로 변경할 예정입니다 — 진행 전 확인해 주세요.
> 
> | ID | 프로젝트명 | 현재 진행률 → 변경 후 |
> |---|---|---|
> | 363 | 명화공업 문서중앙화 내 검색엔진 교체 | 0% → 100% |
> | 343 | 경보제약 검색엔진 교체 | 5% → 100% |
> | 347 | SK온 EUE공장 문서검색엔진 구축 | 5% → 100% |
> | 356 | 삼성선물 문서중앙화 구축 | 0% → 100% |
> | 355 | 치과재료 쇼핑몰 내 검색엔진 구축 | 0% → 100% |
> | 328 | 대상 미생물 정보 종합 플랫폼 구축 | 50% → 100% |
> | 313 | 근로복지공단 스마트 산재보험 시스템 구축 | 99% → 100% |
> 
> (참고: 317 우리은행 문서중앙화 구축은 김경민님이 참여자로 등록되어 있으나 PM은 '김영삼'이라 제외했습니다.)
> 
> 7건 모두 100%로 변경 진행할까요? 확인해 주시면 각 건에 대해 저장 요약을 다시 보여드리고 승인 후 실제 저장하겠습니다.

- DB 결과: [{"projectId":347,"progress":5,"version":0,"auditRows":0}]

## D-05 — 김문수(16)

**T1 사용자** 한국거래소 프로젝트 진행률 50%로 올려줘

- 도구 흐름: `search_projects({keyword=한국거래소})`
- 지연: 5528ms

> "한국거래소" 관련 프로젝트가 여러 건 조회됩니다. 어느 프로젝트를 말씀하시는지 확인 부탁드립니다.
> 
> 1. 한국거래소 차세대 상장공시시스템 구축 (진행중, 현재 10%)
> 2. 한국거래소 경영정보시스템 구축(ERP 및 인사시스템 등 리뉴얼) (진행중, 현재 90%)
> 
> (완료된 프로젝트는 진행률 변경 대상에서 제외했습니다.)

## D-06 — 김경민(18)

**T1 사용자** SK온 EUE공장 진행률 20%로 올려줘

- 도구 흐름: `search_projects({keyword=SK온})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=false})`
- 지연: 8127ms

> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 진행할까요? 확인해 주시면 저장하겠습니다.

**T2 사용자** 취소할게

- 도구 흐름: (호출 없음)
- 지연: 2535ms

> 네, 요청을 취소했습니다. 진행률 변경은 저장되지 않았습니다. 다시 변경을 원하시면 말씀해 주세요.

**T3 사용자** 역시 그냥 올려줘

- 도구 흐름: `search_projects({keyword=SK온 EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=20, version=0, confirmed=false})`
- 지연: 12312ms

> 확인 카드를 다시 보여드립니다.
> 
> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 20% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 저장할까요?

- DB 결과: [{"projectId":347,"progress":5,"version":0,"auditRows":0}]

## D-07 — 김경민(18)

**T1 사용자** 치과재료 쇼핑몰 진행률 150%로 올려줘

- 도구 흐름: `search_projects({keyword=치과재료 쇼핑몰})`
- 지연: 5047ms

> 진행률은 0~100% 범위만 입력할 수 있습니다. 150%는 설정할 수 없으니, 원하시는 정확한 값(0~100 사이)을 알려주시겠어요?

- DB 결과: [{"projectId":355,"progress":0,"version":0,"auditRows":0}]

## D-08 — 김경민(18)

**T1 사용자** SK온 EUE공장 100%로 해줘

- 도구 흐름: `search_projects({keyword=SK온})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=100, version=0, confirmed=false})`
- 지연: 8804ms

> "SK온 EUE공장 문서검색엔진 구축: 진행률 5% → 100% (아직 저장하지 않았습니다 — 사용자 확인 후 저장됩니다)"
> 
> 이대로 저장할까요? 확인해 주시면 반영하겠습니다.

**T2 사용자** 실행해줘

- 도구 흐름: `search_projects({keyword=SK온 EUE공장})` → `search_projects({projectId=347})` → `update_progress({projectId=347, percent=100, version=0, confirmed=false})` → `update_progress({projectId=347, percent=100, version=0, confirmed=true})`
- 지연: 18435ms

> SK온 EUE공장 문서검색엔진 구축 프로젝트의 진행률을 100%로 저장했습니다. 다만 상태를 '완료'로 바꾸는 처리는 PMS 화면에서 별도로 하셔야 합니다.

- DB 결과: [{"projectId":347,"progress":100,"version":1,"auditRows":1}]

