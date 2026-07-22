package kr.proten.pmsmock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PMS 목업 MCP 서버 진입점 — M-1 사전 검증용 임시 앱(구현 가이드 부록 B).
 */
@SpringBootApplication
public class PmsMcpMockApplication {
    public static void main(String[] args) {
        SpringApplication.run(PmsMcpMockApplication.class, args);
    }
}
