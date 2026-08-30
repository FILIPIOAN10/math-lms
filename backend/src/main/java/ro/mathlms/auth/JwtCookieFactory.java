package ro.mathlms.auth;

import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;
import ro.mathlms.user.User;

/**
 * Builds the HttpOnly auth cookie holding a freshly signed JWT. Shared by the
 * Google login success handler and the local login endpoint so the cookie
 * attributes stay identical across both paths.
 */
@Component
public class JwtCookieFactory {

    private final JwtService jwtService;
    private final int expirationMinutes;

    public JwtCookieFactory(JwtService jwtService, AuthProperties authProperties) {
        this.jwtService = jwtService;
        this.expirationMinutes = authProperties.jwtExpirationMinutes();
    }

    public Cookie create(User user) {
        String token = jwtService.generateToken(user);

        Cookie cookie = new Cookie(JwtCookieSuccessHandler.COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // TODO: true in productie (HTTPS)
        cookie.setPath("/");
        cookie.setMaxAge(expirationMinutes * 60);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
}
