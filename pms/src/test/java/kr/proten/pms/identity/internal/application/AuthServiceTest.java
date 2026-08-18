package kr.proten.pms.identity.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.proten.pms.identity.internal.domain.NotifPrefs;
import kr.proten.pms.identity.internal.domain.Person;
import kr.proten.pms.identity.internal.domain.User;
import kr.proten.pms.identity.internal.domain.repository.PersonRepository;
import kr.proten.pms.identity.internal.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 로그인·갱신 유스케이스 단위 테스트 — 실패 사유(미존재·불일치·비활성)는
 * 전부 같은 예외로 수렴해 계정 존재를 탐지할 수 없어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenProvider tokenProvider;

    private AuthService authService;

    // 시드 정합 픽스처 — 전세아(합집합 키스톤 페르소나)를 상정한 임의 id
    private final Person activePerson = new Person(7L, "전세아", 3L, 2L, 4L, 1.0, true, false, true, 0L);
    private final User user = new User(1L, 7L, "jsa@proten.co.kr", "$2a$hash", null, NotifPrefs.allOn(), 0L);

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, personRepository, passwordHasher, tokenProvider);
    }

    @Test
    @DisplayName("로그인 성공 — personId로 access+refresh 쌍 발급")
    void login_validCredentials_issuesTokenPair() {
        // Given
        when(userRepository.findByEmail("jsa@proten.co.kr")).thenReturn(Optional.of(user));
        when(personRepository.findById(7L)).thenReturn(Optional.of(activePerson));
        when(passwordHasher.matches("proten1!", "$2a$hash")).thenReturn(true);
        when(tokenProvider.issue(7L)).thenReturn(new IssuedTokens("access", "refresh"));

        // When
        IssuedTokens tokens = authService.login("jsa@proten.co.kr", "proten1!");

        // Then
        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(tokens.refreshToken()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("미등록 email — InvalidCredentialsException")
    void login_unknownEmail_throwsInvalidCredentials() {
        // Given
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authService.login("nobody@proten.co.kr", "proten1!"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("비밀번호 불일치 — InvalidCredentialsException")
    void login_wrongPassword_throwsInvalidCredentials() {
        // Given
        when(userRepository.findByEmail("jsa@proten.co.kr")).thenReturn(Optional.of(user));
        when(personRepository.findById(7L)).thenReturn(Optional.of(activePerson));
        when(passwordHasher.matches("wrong", "$2a$hash")).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> authService.login("jsa@proten.co.kr", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("soft 삭제된 계정(active=false) — 로그인 차단 (E2-3)")
    void login_inactivePerson_throwsInvalidCredentials() {
        // Given
        Person inactive = new Person(7L, "전세아", 3L, 2L, 4L, 1.0, true, false, false, 0L);
        when(userRepository.findByEmail("jsa@proten.co.kr")).thenReturn(Optional.of(user));
        when(personRepository.findById(7L)).thenReturn(Optional.of(inactive));

        // When / Then
        assertThatThrownBy(() -> authService.login("jsa@proten.co.kr", "proten1!"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("refresh 성공 — 검증 후 새 쌍 발급(회전)")
    void refresh_validToken_rotatesTokenPair() {
        // Given
        when(tokenProvider.verifyRefresh("old-refresh")).thenReturn(7L);
        when(personRepository.findById(7L)).thenReturn(Optional.of(activePerson));
        when(tokenProvider.issue(7L)).thenReturn(new IssuedTokens("new-access", "new-refresh"));

        // When
        IssuedTokens tokens = authService.refresh("old-refresh");

        // Then
        assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("refresh — soft 삭제된 계정이면 거절")
    void refresh_inactivePerson_throwsInvalidToken() {
        // Given
        Person inactive = new Person(7L, "전세아", 3L, 2L, 4L, 1.0, true, false, false, 0L);
        when(tokenProvider.verifyRefresh("old-refresh")).thenReturn(7L);
        when(personRepository.findById(7L)).thenReturn(Optional.of(inactive));

        // When / Then
        assertThatThrownBy(() -> authService.refresh("old-refresh"))
                .isInstanceOf(InvalidTokenException.class);
    }
}
