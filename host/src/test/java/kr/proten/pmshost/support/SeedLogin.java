package kr.proten.pmshost.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실 pms 로그인 토큰 발급 (test 스코프 공용). <b>상수를 복제하지 않는다</b> —
 * email은 시드 정본(`reference/seed/seed_org_proten.sql`)의 `users` 행에서 읽고,
 * 실제로 `/api/auth/login`을 친다. `host/scripts/pms-token.sh`와 같은 규율이다.
 *
 * <p>`/mcp`는 `pms.auth.enabled`와 무관하게 로그인 access 토큰만 받는다(원칙 4).
 * pms는 기동마다 서명 키를 새로 만들므로(AuthKeyConfig) 재기동 뒤의 토큰은 죽는다 —
 * 그래서 캐시하지 않고 부를 때마다 발급한다.
 *
 * <p>비밀번호는 시드가 전원 공용으로 박아 둔 초기값이라 여기서도 상수다 — email과
 * 달리 파일에서 뽑을 자리가 없다(해시만 있다).
 */
public final class SeedLogin {

    private static final String PASSWORD = "proten1!";
    private static final Path SEED =
            Path.of("..", "reference", "seed", "seed_org_proten.sql");

    public static String accessToken(String baseUrl, long personId) {
        return loginClaim(baseUrl, personId, "accessToken");
    }

    public static String refreshToken(String baseUrl, long personId) {
        return loginClaim(baseUrl, personId, "refreshToken");
    }

    private static String loginClaim(String baseUrl, long personId, String field) {
        String email = seedEmail(personId);
        String body = """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD);
        HttpResponse<String> res;
        try (HttpClient http = HttpClient.newHttpClient()) {
            res = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "pms(" + baseUrl + ") 로그인 요청이 실패했다 — 서버가 떠 있는가?", e);
        }

        if (res.statusCode() != 200) {
            throw new IllegalStateException(
                    "로그인 실패 (" + email + ") — " + res.statusCode() + " " + res.body());
        }
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(res.body());
        if (!m.find()) {
            throw new IllegalStateException("로그인 응답에 " + field + "가 없다: " + res.body());
        }

        return m.group(1);
    }

    /** `users` 시드 행 `(id, person_id, 'email', …)`에서 그 사람의 로그인 id를 읽는다 */
    public static String seedEmail(long personId) {
        String sql;
        try {
            sql = Files.readString(SEED, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("시드 정본을 읽지 못했다: " + SEED, e);
        }
        // '@'를 요구해 다른 표의 같은 모양 행이 걸리지 않게 한다
        Matcher m = Pattern.compile("\\(\\s*\\d+,\\s*" + personId + ",\\s*'([^']+@[^']+)'")
                .matcher(sql);
        if (!m.find()) {
            throw new IllegalStateException("시드에서 person " + personId + "의 로그인 email을 찾지 못했다");
        }

        return m.group(1);
    }

    private SeedLogin() {
    }
}
