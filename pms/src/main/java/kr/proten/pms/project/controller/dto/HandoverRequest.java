package kr.proten.pms.project.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.project.HandoverSpec;

/**
 * 이관 요청 (AC D1-1) — 계약 필수 정보 + 사이트 1개 이상.
 *
 * <p>{@code sites}에 {@code @Valid}가 붙어 있어야 목록 <b>안</b>의 제약까지 검사된다 —
 * 없으면 요소의 애너테이션이 조용히 무시된다({@code SiteRequest}가 같은 자리에서
 * 같은 이유로 붙여 뒀다).
 *
 * <p>계약 상태 칸이 없는 것은 누락이 아니다: 이관 계약은 {@code 유지}로 시작한다
 * (서버가 정한다 — {@code HandoverAdapter} 주석).
 *
 * @param version 필수다 — 완료·재개와 같은 이유로 애너테이션이 아니라
 *                {@link #requiredVersion()}이 본다
 */
public record HandoverRequest(
        @NotBlank(message = "계약사는 필수입니다")
        @Size(max = 200, message = "계약사는 200자를 넘을 수 없습니다") String contractor,
        @NotBlank(message = "계약명은 필수입니다")
        @Size(max = 300, message = "계약명은 300자를 넘을 수 없습니다") String name,
        LocalDate startDate,
        LocalDate endDate,
        Long amount,
        Long monthlyAmount,
        @NotEmpty(message = "사이트를 1개 이상 등록해야 합니다")
        @Valid List<SiteRequest> sites,
        Long version) {

    /** 이관과 함께 만드는 사이트 — 담당 엔지니어가 필수다(D1-1). */
    public record SiteRequest(
            @NotBlank(message = "사이트명은 필수입니다")
            @Size(max = 200, message = "사이트명은 200자를 넘을 수 없습니다") String name,
            @NotNull(message = "사이트마다 담당 엔지니어가 필요합니다") Long engineerId) {
    }

    public HandoverSpec toSpec() {
        return new HandoverSpec(contractor, name, startDate, endDate, amount, monthlyAmount,
                sites == null
                        ? List.of()
                        : sites.stream()
                                .map(site -> new HandoverSpec.Site(site.name(), site.engineerId()))
                                .toList());
    }

    /** 없으면 낙관적 락이 조용히 통과해 마지막 쓰기가 이긴다 (AC D1-1). */
    public long requiredVersion() {
        if (version == null) {
            throw new ValidationException("version은 필수입니다", "version");
        }

        return version;
    }
}
