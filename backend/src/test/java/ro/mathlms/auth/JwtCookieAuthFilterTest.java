package ro.mathlms.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCookieAuthFilterTest {

    private JwtService jwtService;
    private JwtCookieAuthFilter filter;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                List.of("profesor@gmail.com"),
                List.of(),
                "test-secret-at-least-32-characters-long!!",
                60);
        jwtService = new JwtService(properties);
        filter = new JwtCookieAuthFilter(jwtService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeTokenPopulatesRoleAndStatusAuthorities() throws Exception {
        User user = User.registerGoogle("google-123", "ana@scoala.ro", "Ana Pop", Role.STUDENT);

        Authentication authentication = authenticateWithTokenFor(user);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("ana@scoala.ro");
        assertThat(authorityNames(authentication))
                .containsExactlyInAnyOrder("ROLE_STUDENT", "STATUS_ACTIVE");
    }

    @Test
    void pendingRolelessTokenPopulatesStatusButNoRole() throws Exception {
        User pending = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);

        Authentication authentication = authenticateWithTokenFor(pending);

        assertThat(authentication).isNotNull();
        assertThat(authorityNames(authentication)).containsExactly("STATUS_PENDING_VERIFICATION");
        assertThat(authorityNames(authentication)).noneMatch(name -> name.startsWith("ROLE_"));
    }

    @Test
    void noCookieLeavesContextUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private Authentication authenticateWithTokenFor(User user) throws Exception {
        String token = jwtService.generateToken(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtCookieSuccessHandler.COOKIE_NAME, token));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static List<String> authorityNames(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
