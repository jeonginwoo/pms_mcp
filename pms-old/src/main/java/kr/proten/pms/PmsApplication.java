package kr.proten.pms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 재구축 PMS 앱 진입점 — Modulith 모듈러 모놀리스.
 * 직속 하위 패키지 6종이 애플리케이션 모듈이다(§3 확정: identity · project ·
 * resource · maintenance · notification · common). /mcp 어댑터 모듈은 MCP 담당이
 * M0에서 추가한다(구조 원칙 2 — 별도 프로세스 금지).
 */
@SpringBootApplication
public class PmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PmsApplication.class, args);
    }
}
