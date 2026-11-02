package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;
import java.util.Set;

/**
 * A Google sign-in that ends without a session goes back to the app's login page, which explains why. Only these fixed
 * codes reach the address, never the email or the text of an error.
 */
public class GoogleLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    /** No account for that email, and no invitation to make one. */
    public static final String NO_ACCOUNT = "no_account";
    /** The invitation the sign-in started from is used up, revoked, expired or tampered with. */
    public static final String INVITE_UNAVAILABLE = "invite_unavailable";
    /** Google has not confirmed that the visitor owns the email it reports. */
    public static final String EMAIL_NOT_VERIFIED = "email_not_verified";
    /** Google's own code: the visitor said no on its page. */
    private static final String CANCELLED = "access_denied";
    private static final String FAILED = "google_failed";
    private static final Set<String> EXPLAINED = Set.of(NO_ACCOUNT, INVITE_UNAVAILABLE, EMAIL_NOT_VERIFIED, CANCELLED);

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginFailureHandler.class);

    /** The login page lives in the app (FRONTEND_BASE_URL), not on this server. */
    private final String frontendBaseUrl;

    public GoogleLoginFailureHandler(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        getRedirectStrategy().sendRedirect(request, response, frontendBaseUrl + "/login?error=" + reason(exception));
    }

    private static String reason(AuthenticationException exception) {
        String code = exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode() : exception.getClass().getSimpleName();
        if (EXPLAINED.contains(code)) {
            return code;
        }
        log.warn("Google sign-in failed: {}", code); // a wrong client secret ends up here
        return FAILED;
    }
}
