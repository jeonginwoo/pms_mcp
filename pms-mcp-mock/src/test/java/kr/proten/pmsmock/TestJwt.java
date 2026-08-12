package kr.proten.pmsmock;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * B2-2 테스트 토큰 발급기 — HS256 자체 서명 (구현_노트 부록 B-2).
 * 클레임 규칙은 §1-2·§1-3과 동일: sub=사용자 id · name · aud=pms · channel=ai-assistant.
 * 수동 검증용 출력: ./gradlew printTestTokens
 */
public final class TestJwt {

    private TestJwt() {
    }

    public static String mint(String secret, int personId, String name, String audience) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(personId))
                    .claim("name", name)
                    .audience(audience)
                    .claim("channel", "ai-assistant")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(8 * 3600)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("테스트 토큰 서명 실패", e);
        }
    }

    /** 기본 그룹 4종 대표 (MockData 시드 id 정합) — 라벨은 콘솔 인코딩 무관하게 ASCII 선행 */
    record Persona(int id, String label, String name) {
    }

    public static void main(String[] args) {
        // 시크릿 정본 = application.yml — printTestTokens 태스크가 읽어 프로퍼티로 넘긴다
        String secret = System.getProperty("mock.jwt.secret");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("mock.jwt.secret 프로퍼티 필요 — ./gradlew printTestTokens로 실행");
        }
        List<Persona> personas = List.of(
                new Persona(1, "ADMIN", "신현랑"),
                new Persona(13, "DIVISION_HEAD", "정태휘"),
                new Persona(16, "TEAM_LEAD", "남도린"),
                new Persona(18, "MEMBER", "전세아"));
        for (Persona p : personas) {
            System.out.printf("id=%d %s (%s) - valid 8h%n%s%n%n",
                    p.id(), p.label(), p.name(), mint(secret, p.id(), p.name(), "pms"));
        }
    }
}
