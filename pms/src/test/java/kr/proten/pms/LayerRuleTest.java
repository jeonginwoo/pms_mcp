package kr.proten.pms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 계층 규칙 검증 — 각 도메인 모듈은 controller → service → repository 한 방향이고,
 * service 하위는 impl(구현)·dto(입출력)·entity(영속 모델)로 나뉜다
 * (2026-08-21 재구축 결정).
 *
 * 의존은 위에서 아래로만 흐른다. 이 테스트가 깨지면 테스트가 아니라 구조를 고친다.
 * 모듈 간 경계(엔티티·리포지토리 상해 금지)는 Modulith가 이름 붙인 인터페이스로
 * 검증하므로 여기서는 계층 방향과 관심사 격리만 본다 — ModularityTest 참조.
 */
class LayerRuleTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("kr.proten.pms");

    @Test
    @DisplayName("repository 계층은 서비스·컨트롤러를 모른다")
    void repositoryDependsOnNoUpperLayer() {
        noClasses().that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..service.impl..",
                        "..controller..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("엔티티는 서비스 구현·컨트롤러를 모른다")
    void entityDependsOnNoUpperLayer() {
        noClasses().that().resideInAPackage("..service.entity..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..service.impl..",
                        "..controller..",
                        "..repository..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("service 계층은 controller를 모른다")
    void serviceDependsOnNoController() {
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("서비스 계약(인터페이스)은 자기 구현을 모른다")
    void serviceContractDependsOnNoImplementation() {
        // "..service" = 이름이 service로 끝나는 모든 패키지 — 도메인 모듈뿐 아니라
        // common 안에 중첩된 계약 패키지(common.audit.service)까지 함께 본다
        noClasses().that().resideInAPackage("..service")
                .should().dependOnClassesThat().resideInAPackage("..service.impl..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("영속 관심사는 엔티티·리포지토리에만 있다 — 엔티티가 곧 도메인 모델이다")
    void persistenceStaysInEntityAndRepository() {
        noClasses().that().resideOutsideOfPackages(
                        "..service.entity..",
                        "..repository..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("웹 관심사는 controller와 common의 에러 변환에만 있다")
    void webStaysInControllerLayer() {
        noClasses().that().resideOutsideOfPackages(
                        "..controller..",
                        "kr.proten.pms.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.http..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("엔티티는 dto를 모른다 — 변환 방향은 dto → 엔티티다")
    void entityDoesNotDependOnDto() {
        noClasses().that().areAnnotatedWith(Entity.class)
                .should().dependOnClassesThat(
                        JavaClass.Predicates.resideInAPackage("..service.dto.."))
                .allowEmptyShould(true)
                .check(classes);
    }
}
