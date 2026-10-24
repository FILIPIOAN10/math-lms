package ro.mathlms.auth;

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
        when(cookieFactory.create(user)).thenReturn(ResponseCookie.from(JwtCookieSuccessHandler.COOKIE_NAME, "jwt").build());
        when(refreshTokens.createSession("ana@scoala.ro")).thenReturn("refresh");
        when(refreshTokens.refreshCookie(any())).thenReturn(ResponseCookie.from("MATHLMS_REFRESH", "refresh").build());
        JwtCookieSuccessHandler handler =
                new JwtCookieSuccessHandler(cookieFactory, refreshTokens, "https://mate.exemplu.ro",
                        org.mockito.Mockito.mock(MfaChallenges.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new TestingAuthenticationToken(new AppOidcUser(mock(OidcUser.class), user), null));

        assertThat(response.getRedirectedUrl()).isEqualTo("https://mate.exemplu.ro");
        assertThat(response.getCookie(JwtCookieSuccessHandler.COOKIE_NAME)).isNotNull();
        assertThat(response.getHeader("Set-Cookie")).isNotNull();
    }

    @Test
    void aGoogleSignInWithTwoStepOnStopsAtTheCodeWithNoSession() throws Exception {
        User user = new User("prof@scoala.ro", "Prof", Role.ADMIN);
        user.enableTwoFactor("criptat", 1L, java.time.Instant.EPOCH);
        MfaChallenges challenges = mock(MfaChallenges.class);
        when(challenges.start(user)).thenReturn(ResponseCookie.from(MfaChallenges.COOKIE, "id").build());
        JwtCookieSuccessHandler handler =
                new JwtCookieSuccessHandler(cookieFactory, refreshTokens, "https://mate.exemplu.ro", challenges);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new TestingAuthenticationToken(new AppOidcUser(mock(OidcUser.class), user), null));

        assertThat(response.getRedirectedUrl()).isEqualTo("https://mate.exemplu.ro/login?step=2fa");
        assertThat(response.getCookie(JwtCookieSuccessHandler.COOKIE_NAME)).isNull();
        assertThat(response.getCookie(MfaChallenges.COOKIE)).isNotNull();
        org.mockito.Mockito.verify(refreshTokens, org.mockito.Mockito.never()).createSession(any());
    }
}
