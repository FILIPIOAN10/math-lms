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
 * Pins the difference between "who are you?" (401) and "I know you, and no" (403) over a real
 * servlet container.
 *
 * <p>MockMvc cannot cover this: it reports {@code sendError} straight to the caller, while a real
 * container forwards to {@code /error} and re-runs the security chain on that ERROR dispatch. Since
 * {@link org.springframework.web.filter.OncePerRequestFilter} skips error dispatches, that second
 * pass is always anonymous — so gating {@code /error} rewrote every 403 into a 401 and hid why a
 * request was actually refused. The MockMvc tests asserting 403 stayed green throughout.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationStatusCodeTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JwtService jwtService;

    private HttpEntity<Void> as(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE,
                JwtCookieSuccessHandler.COOKIE_NAME + "=" + jwtService.generateToken(user));
        return new HttpEntity<>(headers);
    }

    @Test
    void anonymousHittingAnAdminRouteIsUnauthorized() {
        ResponseEntity<String> response =
                rest.getForEntity("/api/admin/users/pending", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticatedNonAdminHittingAnAdminRouteIsForbidden() {
        User student = User.registerGoogle("g-student", "elev@example.com", "Elev", Role.STUDENT);

        ResponseEntity<String> response = rest.exchange(
                "/api/admin/users/pending", HttpMethod.GET, as(student), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminWriteWithoutACsrfTokenIsForbidden() {
        User admin = User.registerGoogle("g-admin", "prof@example.com", "Prof", Role.ADMIN);

        ResponseEntity<String> response = rest.exchange(
                "/api/admin/classes/999", HttpMethod.DELETE, as(admin), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * The long-open-tab case: the SPA still holds a CSRF token, so double-submit passes, but the
     * access cookie has expired. That must read as 401 — the frontend keys its silent token refresh
     * off exactly this status.
     */
    @Test
    void validCsrfWithoutAValidTokenIsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=token-still-in-the-browser");
        headers.add("X-XSRF-TOKEN", "token-still-in-the-browser");

        ResponseEntity<String> response = rest.exchange(
                "/api/admin/classes/999", HttpMethod.DELETE, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
