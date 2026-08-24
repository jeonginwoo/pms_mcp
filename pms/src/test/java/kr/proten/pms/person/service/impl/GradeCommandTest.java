package kr.proten.pms.person.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.proten.pms.common.exception.ConflictException;
import kr.proten.pms.common.exception.ErrorCode;
import kr.proten.pms.common.exception.NotFoundException;
import kr.proten.pms.common.exception.ValidationException;
import kr.proten.pms.person.OrgPermission;
import kr.proten.pms.person.OrgPermissionService;
import kr.proten.pms.person.repository.GradeRepository;
import kr.proten.pms.person.repository.PersonRepository;
import kr.proten.pms.person.service.dto.GradeCommand;
import kr.proten.pms.person.service.dto.GradeDetail;
import kr.proten.pms.person.service.entity.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 직급 관리 쓰기 (AC E4-1·E4-2·E4-3).
 *
 * <p>권한 판정(403)은 {@link ScaffoldAuthorizationTest}가 모든 EPIC E 경로를 한 목록에서
 * 잠그므로 여기서 반복하지 않는다 — 이 테스트는 <b>판정을 통과한 뒤</b>의 규칙만 본다.
 */
@ExtendWith(MockitoExtension.class)
class GradeCommandTest {
    private static final long ADMIN_ID = 1L;
    private static final long GRADE_ID = 5L;

    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private OrgPermissionService orgPermissionService;
    @Mock
    private PersonAuditRecorder auditRecorder;

    private GradeServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(orgPermissionService.has(ADMIN_ID, OrgPermission.MANAGE_ORG))
                .thenReturn(true);
        service = new GradeServiceImpl(gradeRepository, personRepository,
                new OrgManagePermission(orgPermissionService), auditRecorder);
    }

    @Test
    @DisplayName("E4-1 — 새 직급 id는 시퀀스에서 받는다 (max(id)+1이 아니다)")
    void createTakesIdFromSequence() {
        // Given: 삭제된 직급의 id를 다시 내주면 그 직급을 가리키던 비활성 인원과 감사
        //        로그가 엉뚱한 직급을 가리킨다 — 조직 노드에서 실제로 일어난 사고다
        when(gradeRepository.nextId()).thenReturn(42L);
        when(gradeRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // When
        GradeDetail created = service.create(ADMIN_ID, new GradeCommand(null, " 책임 ", 1.2, 0));

        // Then: 이름의 앞뒤 공백은 떼고 저장한다
        assertThat(created.id()).isEqualTo(42L);
        assertThat(created.name()).isEqualTo("책임");
        assertThat(created.coeff()).isEqualTo(1.2);
        verify(auditRecorder).gradeCreated(eqAdmin(), any());
    }

    @Test
    @DisplayName("E4-2 — 수정은 바꾸기 직전 스냅샷을 떠서 바뀐 필드만 남긴다")
    void updateRecordsDiffFromSnapshotTakenBeforeTheChange() {
        Grade grade = Grade.of(GRADE_ID, "책임", 1.2);
        when(gradeRepository.findById(GRADE_ID)).thenReturn(Optional.of(grade));

        GradeDetail updated = service.update(ADMIN_ID, new GradeCommand(GRADE_ID, "수석", 1.5, 0));

        assertThat(updated.name()).isEqualTo("수석");
        assertThat(updated.coeff()).isEqualTo(1.5);
        // 스냅샷을 엔티티 변경 전에 떠야 diff가 성립한다 — 순서가 뒤집히면 before==after다
        verify(auditRecorder).snapshot(grade);
        verify(auditRecorder).gradeChanged(eqAdmin(), any(), any());
    }

    @Test
    @DisplayName("E4-3 — 쓰는 인원이 있으면 409 IN_USE이고 행은 남는다")
    void deleteIsRejectedWhileInUse() {
        when(gradeRepository.findById(GRADE_ID))
                .thenReturn(Optional.of(Grade.of(GRADE_ID, "책임", 1.2)));
        when(personRepository.existsByGradeId(GRADE_ID)).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.delete(ADMIN_ID, GRADE_ID))
                .satisfies(thrown -> assertThat(thrown.code()).isEqualTo(ErrorCode.IN_USE));

        verify(gradeRepository, never()).delete(any());
        verify(auditRecorder, never()).gradeDeleted(eqAdmin(), any());
    }

    @Test
    @DisplayName("E4-3 — 아무도 쓰지 않으면 삭제하고 이력을 남긴다")
    void deleteRemovesUnusedGrade() {
        Grade grade = Grade.of(GRADE_ID, "책임", 1.2);
        when(gradeRepository.findById(GRADE_ID)).thenReturn(Optional.of(grade));
        when(personRepository.existsByGradeId(GRADE_ID)).thenReturn(false);

        service.delete(ADMIN_ID, GRADE_ID);

        // 이력을 먼저 남긴다 — 지운 뒤에는 무엇을 지웠는지 읽을 수 없다
        verify(auditRecorder).gradeDeleted(eqAdmin(), eqGrade(grade));
        verify(gradeRepository).delete(grade);
    }

    @Test
    @DisplayName("없는 직급을 수정·삭제하면 404")
    void missingGradeIsNotFound() {
        when(gradeRepository.findById(GRADE_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.delete(ADMIN_ID, GRADE_ID));
    }

    @Test
    @DisplayName("계수는 0보다 커야 한다 — 0이면 보정 가동률이 전부 0이 된다")
    void coeffMustBePositive() {
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, new GradeCommand(null, "책임", 0, 0)))
                .satisfies(thrown -> assertThat(thrown.field()).isEqualTo("coeff"));

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, new GradeCommand(null, "책임", -1, 0)));
    }

    @Test
    @DisplayName("직급명은 필수다")
    void nameIsRequired() {
        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.create(ADMIN_ID, new GradeCommand(null, "  ", 1.2, 0)))
                .satisfies(thrown -> assertThat(thrown.field()).isEqualTo("name"));
    }

    private static long eqAdmin() {
        return org.mockito.ArgumentMatchers.eq(ADMIN_ID);
    }

    private static Grade eqGrade(Grade grade) {
        return org.mockito.ArgumentMatchers.eq(grade);
    }
}
