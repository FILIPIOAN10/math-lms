package ro.mathlms.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import ro.mathlms.user.User;

import java.time.Duration;

/**
 * Builds the HttpOnly auth cookie holding a freshly signed JWT. Shared by the
 * Google login success handler and the local login endpoint so the cookie
 * attributes stay identical across both paths. Built and sent like the refresh
 * cookie ({@link RefreshTokenService}): a {@link ResponseCookie} in a Set-Cookie header.
 *
 * <p>{@code Secure} is on by default and only disabled for local plain-HTTP dev
 * ({@code app.auth.cookie-secure=false}). A site served over https never runs without
 * it: the app refuses to start with {@code cookie-secure=false} and an https frontend.
 */
@Component
public class JwtCookieFactory {

    private final JwtService jwtService;
    private final int expirationMinutes;
    private final boolean cookieSecure;

    public JwtCookieFactory(JwtService jwtService, AuthProperties authProperties,
                            @Value("${app.auth.cookie-secure:true}") boolean cookieSecure,
                            @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        requireSecureOverHttps(cookieSecure, frontendBaseUrl);
        this.jwtService = jwtService;
        this.expirationMinutes = authProperties.jwtExpirationMinutes();
        this.cookieSecure = cookieSecure;
    }

    /** {@code cookie-secure=false} is for http://localhost only: on an https site it would leak tokens to plain http. */
    static void requireSecureOverHttps(boolean cookieSecure, String frontendBaseUrl) {
        if (!cookieSecure && frontendBaseUrl != null && frontendBaseUrl.strip().toLowerCase().startsWith("https://")) {
            throw new IllegalStateException("app.auth.cookie-secure=false (COOKIE_SECURE) is only for local http dev, "
                    + "but the frontend is " + frontendBaseUrl + ": set COOKIE_SECURE=true");
        }
    }

    public ResponseCookie create(User user) {
        return cookie(jwtService.generateToken(user), Duration.ofMinutes(expirationMinutes));
    }

    /** The logout version: same name and attributes, empty and already expired, so the browser drops its copy. */
    public ResponseCookie expired() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(JwtCookieSuccessHandler.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }
}
