package ro.mathlms.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * After Google sign-in the browser goes to the app's own address. It was hard-coded to http://localhost:5173, so in
 * production a Google login ended on the user's own machine instead of the site.
 */
class JwtCookieSuccessHandlerTest {

    private final JwtCookieFactory cookieFactory = mock(JwtCookieFactory.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);

    @Test
    void aGoogleSignInEndsOnTheConfiguredFrontendWithBothCookies() throws Exception {
        User user = new User("ana@scoala.ro", "Ana", Role.STUDENT);
        when(cookieFactory.create(user)).thenReturn(new Cookie(JwtCookieSuccessHandler.COOKIE_NAME, "jwt"));
        when(refreshTokens.createSession("ana@scoala.ro")).thenReturn("refresh");
        when(refreshTokens.refreshCookie(any())).thenReturn(ResponseCookie.from("MATHLMS_REFRESH", "refresh").build());
        JwtCookieSuccessHandler handler =
                new JwtCookieSuccessHandler(cookieFactory, refreshTokens, "https://mate.exemplu.ro");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new TestingAuthenticationToken(new AppOidcUser(mock(OidcUser.class), user), null));

        assertThat(response.getRedirectedUrl()).isEqualTo("https://mate.exemplu.ro");
        assertThat(response.getCookie(JwtCookieSuccessHandler.COOKIE_NAME)).isNotNull();
        assertThat(response.getHeader("Set-Cookie")).isNotNull();
    }
}
