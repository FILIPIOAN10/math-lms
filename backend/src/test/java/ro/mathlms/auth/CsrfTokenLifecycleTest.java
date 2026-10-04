package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SPA's double-submit CSRF token must survive ordinary authenticated traffic.
 *
 * <p>Authentication is stateless: {@link JwtCookieAuthFilter} re-authenticates every request, and
 * with a STATELESS session policy {@code SessionManagementFilter} treats each one as a fresh login.
 * The default {@code CsrfAuthenticationStrategy} then "rotated" the token on every request — it
 * deleted the XSRF-TOKEN cookie and, since nothing materialised the replacement, wrote no new one.
 * So after any read the browser had no token to echo and the next write failed with 403. Only a
 * real round trip shows the Set-Cookie header, hence a random-port server rather than MockMvc.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsrfTokenLifecycleTest {

    private static final String CSRF_TOKEN = "token-in-the-browser";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JwtService jwtService;

    private HttpHeaders withSessionAndCsrfCookie(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, JwtCookieSuccessHandler.COOKIE_NAME + "=" + jwtService.generateToken(user)
                + "; XSRF-TOKEN=" + CSRF_TOKEN);
        return headers;
    }

    @Test
    void authenticatedReadLeavesTheCsrfCookieAlone() {
        User student = User.registerGoogle("g-student", "elev@example.com", "Elev", Role.STUDENT);

        // Any authenticated read will do; /api/classes needs no account row (the JWT user is not persisted here)
        ResponseEntity<String> response = rest.exchange("/api/classes", HttpMethod.GET,
                new HttpEntity<>(withSessionAndCsrfCookie(student)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.startsWith("XSRF-TOKEN="));
    }

    @Test
    void writeEchoingTheCsrfCookiePassesTheCsrfCheck() {
        User admin = User.registerGoogle("g-admin", "prof@example.com", "Prof", Role.ADMIN);
        HttpHeaders headers = withSessionAndCsrfCookie(admin);
        headers.add("X-XSRF-TOKEN", CSRF_TOKEN);

        ResponseEntity<String> response = rest.exchange("/api/admin/classes/999", HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);

        // Past the CSRF filter and the ADMIN rule: the class simply does not exist.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
