package ro.mathlms.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtCookieFactoryTest {

    private JwtService jwtService;
    private JwtCookieFactory factory;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                List.of("profesor@gmail.com"),
                List.of(),
                "test-secret-at-least-32-characters-long!!",
                60);
        jwtService = new JwtService(properties);
        factory = new JwtCookieFactory(jwtService, properties, true, "https://mate.exemplu.ro");
    }

    @Test
    void buildsHttpOnlySecureCookieCarryingASignedToken() {
        User user = new User("ana@scoala.ro", "Ana Pop", Role.STUDENT);

        ResponseCookie cookie = factory.create(user);

        assertThat(cookie.getName()).isEqualTo(JwtCookieSuccessHandler.COOKIE_NAME);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(60));
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(jwtService.extractEmail(cookie.getValue())).isEqualTo("ana@scoala.ro");
    }

    @Test
    void secureFlagFollowsConfigForLocalHttpDev() {
        JwtCookieFactory devFactory = new JwtCookieFactory(jwtService,
                new AuthProperties(List.of(), List.of(), "test-secret-at-least-32-characters-long!!", 60),
                false, "http://localhost:5173");

        ResponseCookie cookie = devFactory.create(new User("ana@scoala.ro", "Ana Pop", Role.STUDENT));

        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    void anHttpsSiteRefusesToStartWithoutSecureCookies() {
        AuthProperties properties = new AuthProperties(List.of(), List.of(),
                "test-secret-at-least-32-characters-long!!", 60);

        assertThatThrownBy(() -> new JwtCookieFactory(jwtService, properties, false, "https://mate.exemplu.ro"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COOKIE_SECURE=true");
        assertThatThrownBy(() -> JwtCookieFactory.requireSecureOverHttps(false, " HTTPS://mate.exemplu.ro"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theLogoutCookieMatchesTheOneItDeletesButIsEmptyAndExpired() {
        ResponseCookie cleared = factory.expired();

        assertThat(cleared.getName()).isEqualTo(JwtCookieSuccessHandler.COOKIE_NAME);
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.isHttpOnly()).isTrue();
        assertThat(cleared.isSecure()).isTrue(); // was missing: CodeQL java/insecure-cookie
        assertThat(cleared.getPath()).isEqualTo("/");
        assertThat(cleared.getSameSite()).isEqualTo("Lax");
    }
}
