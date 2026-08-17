package kr.proten.pms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 도메인 레이어 순수성 검증 (PRD-pms §0: "domain은 Spring/JPA import 0").
 * 각 모듈의 internal/domain 패키지는 프레임워크를 모른 채 도메인 규칙만 가진다.
 * 스캐폴드 시점에는 도메인 클래스가 없어 공집합 통과 — PMS-M1부터 실효.
 */
class DomainPurityTest {
    @Test
    @DisplayName("..domain.. 은 Spring·JPA에 의존하지 않는다")
    void domainDependsOnNeitherSpringNorJpa() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("kr.proten.pms");

        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
