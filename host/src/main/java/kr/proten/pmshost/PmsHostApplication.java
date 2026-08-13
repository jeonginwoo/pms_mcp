package kr.proten.pmshost;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PmsHostApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmsHostApplication.class, args);
    }

    /** "이번 달"류 상대 시점의 기준 — 테스트에서 고정 시각으로 대체 */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

}
