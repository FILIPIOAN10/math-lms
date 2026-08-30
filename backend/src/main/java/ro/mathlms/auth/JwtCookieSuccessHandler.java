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

    private final JwtCookieFactory jwtCookieFactory;

    public JwtCookieSuccessHandler(JwtCookieFactory jwtCookieFactory) {
        this.jwtCookieFactory = jwtCookieFactory;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        AppOidcUser principal = (AppOidcUser) authentication.getPrincipal();
        User user = principal.getUser();

        response.addCookie(jwtCookieFactory.create(user));

        getRedirectStrategy().sendRedirect(request, response, "http://localhost:5173");
    }
}
