package kr.proten.pms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 재구축 PMS 앱 진입점 — 도메인별 Modulith 모듈러 모놀리스.
 * 직속 하위 패키지가 애플리케이션 모듈이다: person(사람·조직·직급·권한 그룹) ·
 * project(프로젝트·배정) · common(에러 봉투 등 최소 공통분모).
 * 각 모듈은 3계층(controller → service → repository)으로 구성된다.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PmsApplication.class, args);
    }
}
