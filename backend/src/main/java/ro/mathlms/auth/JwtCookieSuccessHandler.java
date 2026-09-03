package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import ro.mathlms.user.User;

import java.io.IOException;

@Component
public class JwtCookieSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String COOKIE_NAME = "MATHLMS_TOKEN";

    private final JwtCookieFactory jwtCookieFactory;
    private final RefreshTokenService refreshTokenService;

    public JwtCookieSuccessHandler(JwtCookieFactory jwtCookieFactory, RefreshTokenService refreshTokenService) {
        this.jwtCookieFactory = jwtCookieFactory;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        AppOidcUser principal = (AppOidcUser) authentication.getPrincipal();
        User user = principal.getUser();

        response.addCookie(jwtCookieFactory.create(user));
        String refreshToken = refreshTokenService.createSession(user.getEmail());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenService.refreshCookie(refreshToken).toString());

        getRedirectStrategy().sendRedirect(request, response, "http://localhost:5173");
    }
}
