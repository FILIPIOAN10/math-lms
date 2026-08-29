package ro.mathlms.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import ro.mathlms.user.User;

import java.io.IOException;

@Component
public class JwtCookieSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String COOKIE_NAME = "MATHLMS_TOKEN";

    private final JwtService jwtService;
    private final int expirationMinutes;

    public JwtCookieSuccessHandler(JwtService jwtService, AuthProperties authProperties) {
        this.jwtService = jwtService;
        this.expirationMinutes = authProperties.jwtExpirationMinutes();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        AppOidcUser principal = (AppOidcUser) authentication.getPrincipal();
        User user = principal.getUser();

        String token = jwtService.generateToken(user);

        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // TODO: true in productie (HTTPS)
        cookie.setPath("/");
        cookie.setMaxAge(expirationMinutes * 60);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);

        getRedirectStrategy().sendRedirect(request, response, "http://localhost:5173");
    }
}
