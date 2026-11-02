package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

/** A Google sign-in that ends without a session lands on the app's login page, with a code it knows how to explain. */
class GoogleLoginFailureHandlerTest {

    private final GoogleLoginFailureHandler handler = new GoogleLoginFailureHandler("https://mate.exemplu.ro");

    private String redirectAfter(AuthenticationException failure) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, failure);
        return response.getRedirectedUrl();
    }

    private static OAuth2AuthenticationException oauth(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), "nou@gmail.com nu are voie");
    }

    @Test
    void theReasonsTheAppKnowsGoBackToTheLoginPage() throws Exception {
        assertThat(redirectAfter(oauth(GoogleLoginFailureHandler.NO_ACCOUNT)))
                .isEqualTo("https://mate.exemplu.ro/login?error=no_account");
        assertThat(redirectAfter(oauth(GoogleLoginFailureHandler.INVITE_UNAVAILABLE)))
                .isEqualTo("https://mate.exemplu.ro/login?error=invite_unavailable");
        assertThat(redirectAfter(oauth("access_denied"))).isEqualTo("https://mate.exemplu.ro/login?error=access_denied");
    }

    @Test
    void anythingElseIsAGeneralFailureWithNothingFromTheErrorInTheAddress() throws Exception {
        assertThat(redirectAfter(oauth("invalid_token_response"))).isEqualTo("https://mate.exemplu.ro/login?error=google_failed");
        assertThat(redirectAfter(new BadCredentialsException("nou@gmail.com")))
                .isEqualTo("https://mate.exemplu.ro/login?error=google_failed");
    }
}
