package com.proten.pms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PMS 백엔드 부트스트랩.
 * 이 패키지의 직계 하위 패키지가 Spring Modulith 모듈이 된다 (common부터 시작).
 */
@SpringBootApplication
public class PmsApplication {
    /**
     * 애플리케이션 진입점.
     */
    public static void main(String[] args) {
        SpringApplication.run(PmsApplication.class, args);
    }
}
