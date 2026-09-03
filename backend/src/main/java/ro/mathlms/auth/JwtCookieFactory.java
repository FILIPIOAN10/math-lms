package ro.mathlms.auth;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.mathlms.user.User;

/**
 * Builds the HttpOnly auth cookie holding a freshly signed JWT. Shared by the
 * Google login success handler and the local login endpoint so the cookie
 * attributes stay identical across both paths.
 *
 * <p>{@code Secure} is on by default and only disabled for local plain-HTTP dev
 * ({@code app.auth.cookie-secure=false}); production over HTTPS keeps it true so
 * the token is never sent over cleartext.
 */
@Component
public class JwtCookieFactory {

    private final JwtService jwtService;
    private final int expirationMinutes;
    private final boolean cookieSecure;

    public JwtCookieFactory(JwtService jwtService, AuthProperties authProperties,
                            @Value("${app.auth.cookie-secure:true}") boolean cookieSecure) {
        this.jwtService = jwtService;
        this.expirationMinutes = authProperties.jwtExpirationMinutes();
        this.cookieSecure = cookieSecure;
    }

    public Cookie create(User user) {
        String token = jwtService.generateToken(user);

        Cookie cookie = new Cookie(JwtCookieSuccessHandler.COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(expirationMinutes * 60);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
}
