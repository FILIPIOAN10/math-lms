package ro.mathlms.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        factory = new JwtCookieFactory(jwtService, properties);
    }

    @Test
    void buildsHttpOnlyCookieCarryingASignedToken() {
        User user = new User("ana@scoala.ro", "Ana Pop", Role.STUDENT);

        Cookie cookie = factory.create(user);

        assertThat(cookie.getName()).isEqualTo(JwtCookieSuccessHandler.COOKIE_NAME);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(60 * 60);
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(jwtService.extractEmail(cookie.getValue())).isEqualTo("ana@scoala.ro");
    }
}
