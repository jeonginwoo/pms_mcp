package kr.proten.pms.maintenance.service.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.proten.pms.maintenance.service.entity.ContactDetails;
import org.springframework.stereotype.Component;

/**
 * 시트 담당자 문자열에서 조각을 뽑는다 — <b>확실한 것만</b> (2026-08-23 결정).
 *
 * <p>원문 형식이 네 가지 이상 섞여 있다:
 * <pre>
 *   이준혁 사원 02-2140-5773 (wnsgur0718@kaoni.com)   이름·직급·전화·이메일
 *   043-717-7822                                      전화만
 *   에스에프존 김승윤 차장 010-4849-7117 (ksy@…)      회사명이 앞에 붙음
 *   에스에프존 이희진(원격) 사원 010-8892-9749 (…)    이름에 괄호 주석
 * </pre>
 *
 * <p>그래서 <b>전화와 이메일만</b> 정규식으로 뽑는다 — 둘은 형태가 스스로를 증명한다.
 * 이름·직급은 남은 토큰에서 보수적으로 추정하고, 확신이 없으면 비운다. 원문은
 * {@code raw}에 그대로 남으므로 여기서 못 뽑은 것이 사라지지는 않는다.
 */
@Component
public class ContactParser {
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // 02-1234-5678 · 010-1234-5678 · 043-717-7822 — 구분자는 하이픈만 쓰인다
    private static final Pattern PHONE = Pattern.compile("\\d{2,3}-\\d{3,4}-\\d{4}");
    // 시트에 실제로 등장하는 직급어. 목록에 없으면 직급을 비운다 — 추측하지 않는다
    private static final Pattern TITLE =
            Pattern.compile("(대표|부사장|상무|이사|수석|책임|선임|부장|차장|과장|대리|주임|사원|매니저)");
    private static final Pattern KOREAN_NAME = Pattern.compile("[가-힣]{2,4}");

    public ContactDetails parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ContactDetails.empty();
        }

        String email = firstMatch(EMAIL, raw);
        String phone = firstMatch(PHONE, raw);
        String title = firstMatch(TITLE, raw);
        String name = nameOf(raw, title);

        return new ContactDetails(name, title, phone, email);
    }

    /**
     * 이름은 <b>직급 바로 앞의 한글 토큰</b>으로 본다 — 시트가 "회사 이름 직급 전화"
     * 순으로 적혀 있어 직급이 이름의 오른쪽 경계 역할을 한다. 직급이 없으면(전화만
     * 있는 행 등) 이름도 비운다: 첫 한글 토큰을 집으면 회사명을 이름으로 넣게 된다.
     */
    private static String nameOf(String raw, String title) {
        if (title == null) {
            return null;
        }

        String beforeTitle = raw.substring(0, raw.indexOf(title));
        // 괄호 주석("이희진(원격)")은 이름의 일부가 아니다
        String cleaned = beforeTitle.replaceAll("\\([^)]*\\)", " ").trim();
        Matcher matcher = KOREAN_NAME.matcher(cleaned);
        String last = null;

        while (matcher.find()) {
            last = matcher.group();
        }

        return last;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);

        return matcher.find() ? matcher.group() : null;
    }
}
