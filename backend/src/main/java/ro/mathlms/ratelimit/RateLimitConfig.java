package ro.mathlms.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * The rule table — the ONE place that decides what is limited. Order matters: the first rule matching
 * (method + path) wins, so specific patterns go before broad ones. {@code rate.limit.enabled=false} empties the
 * list and turns the filter into a no-op (used by tests and by the local E2E suite, which logs in dozens of
 * times a minute from one IP).
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public List<RateLimitRule> rateLimitRules(@Value("${rate.limit.enabled:true}") boolean enabled) {
        if (!enabled) {
            return Collections.emptyList();
        }
        Duration minute = Duration.ofMinutes(1);
        return List.of(
                // Brute force / enumeration targets: tight, per IP.
                new RateLimitRule("login", "POST", "/api/auth/login", 5, minute, RateLimitKeyType.IP),
                new RateLimitRule("forgot-password", "POST", "/api/auth/forgot-password", 3, Duration.ofMinutes(15), RateLimitKeyType.IP),
                new RateLimitRule("reset-password", "POST", "/api/auth/reset-password", 5, Duration.ofMinutes(15), RateLimitKeyType.IP),
                new RateLimitRule("register", "POST", "/api/auth/register", 5, Duration.ofHours(1), RateLimitKeyType.IP),
                // The SPA refreshes silently on every 401, so this one is deliberately roomy.
                new RateLimitRule("refresh", "POST", "/api/auth/refresh", 30, minute, RateLimitKeyType.IP),
                // Expensive per-student write: the photo upload (image decoding + disk).
                new RateLimitRule("photo-upload", "POST", "/api/quiz/attempts/*/responses/*/photo", 20, minute, RateLimitKeyType.USER)
        );
    }

    /**
     * Puts the filter in the servlet chain AFTER Spring Security (whose chain has a negative order), so the
     * SecurityContext is already populated when a USER-keyed rule asks who the caller is.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RedisRateLimitService service, List<RateLimitRule> rules, RateLimitPrincipalResolver principalResolver,
            @Value("${rate.limit.trust-x-forwarded-for:false}") boolean trustForwardedFor) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(service, rules, principalResolver, trustForwardedFor));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
