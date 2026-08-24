package kr.proten.pms.maintenance.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.maintenance.service.dto.ContractCommand;
import kr.proten.pms.maintenance.service.entity.ContractStatus;

/**
 * 계약 등록·수정 요청 (AC D2-1·D2-2) — 전체 치환(PUT) 본문이고 등록도 같은 모양이다.
 *
 * 시트 유래 필드(sheetSection·contractDateNote)와 sourceProjectId가 없는 것은
 * {@code ContractCommand} 주석의 이유 그대로다 — 화면이 채울 값이 아니다.
 *
 * 길이 제약은 스키마 폭(V9)을 그대로 옮긴 것이다: 없으면 넘치는 입력이 DB까지 가서
 * 500으로 나가고, 있으면 400 VALIDATION_ERROR로 필드명과 함께 돌아온다(§7).
 *
 * 등록과 수정이 한 record인 이유: 두 요청의 필드가 완전히 같고 차이는 {@code version}
 * 하나뿐이다. project가 {@code CreateProjectRequest}·{@code EditProjectRequest}로 나눈
 * 것은 모양이 실제로 달라서였다(등록에는 status가 없다). 여기서 나누면 13개 필드와
 * 그 검증 애너테이션이 두 벌이 되고, 한쪽만 고쳐지는 날이 온다.
 *
 * @param version 수정에서만 쓴다 — 등록 요청에서는 비어 있어도 된다
 */
public record ContractRequest(
        @NotBlank(message = "계약사는 필수입니다")
        @Size(max = 200, message = "계약사는 200자를 넘을 수 없습니다") String contractor,
        @NotBlank(message = "계약명은 필수입니다")
        @Size(max = 300, message = "계약명은 300자를 넘을 수 없습니다") String name,
        @NotNull(message = "계약 상태는 필수입니다") ContractStatus status,
        LocalDate contractDate,
        LocalDate startDate,
        LocalDate endDate,
        @PositiveOrZero(message = "계약금액은 0 이상이어야 합니다") Long amount,
        @PositiveOrZero(message = "월간금액은 0 이상이어야 합니다") Long monthlyAmount,
        Long salesRepId,
        @Size(max = 60, message = "대분류는 60자를 넘을 수 없습니다") String category,
        @Size(max = 200, message = "제품 사양은 200자를 넘을 수 없습니다") String targetInfra,
        @Size(max = 300, message = "정기점검은 300자를 넘을 수 없습니다") String regularCheck,
        String note,
        Long version) {

    public ContractCommand toCommand() {
        return new ContractCommand(contractor, name, status, contractDate, startDate, endDate,
                amount, monthlyAmount, salesRepId, category, targetInfra, regularCheck, note);
    }

    /**
     * 수정에서만 필수다 (AC D2-2) — 애너테이션으로 표현할 수 없는 조건부 필수라
     * 여기서 본다. 없으면 낙관적 락이 조용히 통과해 마지막 쓰기가 이긴다.
     */
    public long requiredVersion() {
        if (version == null) {
            throw new ValidationException("version은 필수입니다", "version");
        }

        return version;
    }
}
