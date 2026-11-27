package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.SecurityAuditLog;
import ro.mathlms.user.User;

import java.io.IOException;

@Component
public class JwtCookieSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String COOKIE_NAME = "MATHLMS_TOKEN";

    private final JwtCookieFactory jwtCookieFactory;
    private final RefreshTokenService refreshTokenService;
    /** Where the browser goes after Google sign-in: the app itself (FRONTEND_BASE_URL), never a dev address. */
    private final String frontendBaseUrl;
    private final MfaChallenges mfaChallenges;
    private final SecurityAuditLog securityAuditLog;

    @Autowired
    public JwtCookieSuccessHandler(JwtCookieFactory jwtCookieFactory, RefreshTokenService refreshTokenService,
                                   @Value("${app.frontend.base-url}") String frontendBaseUrl, MfaChallenges mfaChallenges,
                                   SecurityAuditLog securityAuditLog) {
        this.jwtCookieFactory = jwtCookieFactory;
        this.refreshTokenService = refreshTokenService;
        this.frontendBaseUrl = frontendBaseUrl;
        this.mfaChallenges = mfaChallenges;
        this.securityAuditLog = securityAuditLog;
    }


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, jakarta.servlet.ServletException {
        AppOidcUser principal = (AppOidcUser) authentication.getPrincipal();
        User user = principal.getUser();

        if (user.isTwoFactorEnabled()) {
            // F8: Google proved the email; the authenticator code still has to - no session until it does.
            response.addHeader(HttpHeaders.SET_COOKIE, mfaChallenges.start(user).toString());
            securityAuditLog.record(AuditAction.LOGIN_CHALLENGE_STARTED, user.getId(), "Google acceptat; se cere 2FA");
            getRedirectStrategy().sendRedirect(request, response, frontendBaseUrl + "/login?step=2fa");
            return;
        }

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieFactory.create(user).toString());
        String refreshToken = refreshTokenService.createSession(user.getEmail());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenService.refreshCookie(refreshToken).toString());
        securityAuditLog.record(AuditAction.LOGIN_SUCCEEDED, user.getId(), "Conectare cu Google");

        getRedirectStrategy().sendRedirect(request, response, frontendBaseUrl);
    }
}
