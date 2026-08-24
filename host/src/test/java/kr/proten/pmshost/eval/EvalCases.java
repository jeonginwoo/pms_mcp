package kr.proten.pmshost.eval;

import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * eval 케이스의 실행용 기계 판(`eval/cases.json`) 로더.
 *
 * <p><b>정본은 `docs/evals/eval-cases.md`다.</b> 이 파일이 나르는 것은 러너가 밟을 수
 * 있는 것 — 화자·발화·대화 체인·주입 지시뿐이고 <b>기대값은 담지 않는다</b>. 기대값을
 * 여기 옮기면 원장이 둘이 되고, 그때부터 채점이 어느 쪽을 보는지가 사람마다 갈린다.
 * 두 벌의 정합은 {@link EvalCasesDriftTest}가 고정한다(불일치 시 문서가 이긴다).
 */
final class EvalCases {

    /** 러너가 도는 순서 = 원장 순서. 순서가 의미를 갖는다(cases.json `_note`). */
    static List<Case> load() {
        try (InputStream in = EvalCases.class.getResourceAsStream("/eval/cases.json")) {
            if (in == null) {
                throw new IllegalStateException("eval/cases.json이 테스트 클래스패스에 없다");
            }

            return new ObjectMapper().readValue(in, Ledger.class).cases();
        } catch (Exception e) {
            throw new IllegalStateException("eval/cases.json을 읽지 못했다", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ledger(List<Case> cases) {
    }

    /**
     * @param continues  이어받을 앞 케이스 id — 있으면 그 대화의 맥락을 그대로 잇는다
     * @param restore    실행 전에 시드 기준선으로 되돌릴 프로젝트 id
     * @param inject     주입 장치 지시 (없으면 null)
     * @param anySpeaker 원장이 화자를 "(임의 화자)"로 둔 케이스
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Case(
            String id,
            Speaker speaker,
            List<String> turns,
            String continues,
            List<Long> restore,
            Inject inject,
            boolean anySpeaker) {

        public List<Long> restore() {
            return restore == null ? List.of() : restore;
        }

        boolean hasFault() {
            return inject != null && "toolFault".equals(inject.kind());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Speaker(long personId, String name) {
    }

    /**
     * @param kind       contaminate(오염 레코드) | concurrentWrite(동시 수정) | toolFault(도구 오류)
     * @param beforeTurn concurrentWrite가 끼어드는 자리 — 이 턴 <b>직전</b>에 실행된다(1-based)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Inject(
            String kind,
            long issueId,
            long authorId,
            String text,
            int beforeTurn,
            long projectId,
            int progress) {
    }

    private EvalCases() {
    }
}
