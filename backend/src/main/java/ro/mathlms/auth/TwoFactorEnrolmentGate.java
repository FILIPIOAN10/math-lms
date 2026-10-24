package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ro.mathlms.user.UserRepository;

import java.io.IOException;

/**
 * F8: an ADMIN who has not set up two-step sign-in yet gets 403 on every staff endpoint ({@code /api/admin/**}) until
 * they do — on "Contul meu", which stays open. Runs after Spring Security (an MVC interceptor), so it sees the role.
 * Off with {@code app.auth.two-factor.required-for-admin=false} (the test suite, local dev).
 */
@Configuration
@ConditionalOnProperty(name = "app.auth.two-factor.required-for-admin", havingValue = "true", matchIfMissing = true)
public class TwoFactorEnrolmentGate implements WebMvcConfigurer, HandlerInterceptor {

    /** Body of the 403, so the page can send the admin to set it up (not a generic "forbidden"). */
    public static final String SETUP_REQUIRED = "TWO_FACTOR_SETUP_REQUIRED";

    private final UserRepository userRepository;
    private final TwoFactorService twoFactor;

    public TwoFactorEnrolmentGate(UserRepository userRepository, TwoFactorService twoFactor) {
        this.userRepository = userRepository;
        this.twoFactor = twoFactor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/admin/**");
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin || userRepository.findByEmail(auth.getName()).filter(twoFactor::mustEnrol).isEmpty()) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(SETUP_REQUIRED);
        return false;
    }
}
