package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
 * Staff who have not set up required two-step sign-in get 403 on protected API routes until they do. Their account and
 * MFA setup routes stay open so they can enrol. Runs after Spring Security (an MVC interceptor), so it sees the role.
 * Registered when either staff role has MFA marked as required.
 */
@Configuration
@ConditionalOnExpression("'${app.auth.two-factor.required-for-admin:true}' == 'true' or "
        + "'${app.auth.two-factor.required-for-teacher:${app.auth.two-factor.required-for-admin:true}}' == 'true'")
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
        registry.addInterceptor(this).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/me/**", "/api/public/**");
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || userRepository.findByEmail(auth.getName()).filter(twoFactor::mustEnrol).isEmpty()) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(SETUP_REQUIRED);
        return false;
    }
}
