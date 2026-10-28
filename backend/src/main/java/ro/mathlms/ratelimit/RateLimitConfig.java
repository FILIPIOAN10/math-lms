package ro.mathlms.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
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
                // 6-digit codes: on top of the 5 attempts per sign-in, a cap per IP.
                new RateLimitRule("two-factor", "POST", "/api/auth/2fa/verify", 10, minute, RateLimitKeyType.IP),
                new RateLimitRule("forgot-password", "POST", "/api/auth/forgot-password", 3, Duration.ofMinutes(15), RateLimitKeyType.IP),
                new RateLimitRule("reset-password", "POST", "/api/auth/reset-password", 5, Duration.ofMinutes(15), RateLimitKeyType.IP),
                new RateLimitRule("register", "POST", "/api/auth/register", 5, Duration.ofHours(1), RateLimitKeyType.IP),
                // The SPA refreshes silently on every 401, so this one is deliberately roomy.
                new RateLimitRule("refresh", "POST", "/api/auth/refresh", 30, minute, RateLimitKeyType.IP),
                // Guessing the current password from a stolen session, and erasure requests that each send an email.
                new RateLimitRule("change-password", "POST", "/api/me/password", 5, Duration.ofMinutes(15), RateLimitKeyType.USER),
                new RateLimitRule("gdpr-erase", "POST", "/api/users/gdpr/erase", 3, Duration.ofHours(1), RateLimitKeyType.USER),
                // Each one builds a whole account into a ZIP (or emails a link): bounded per person.
                new RateLimitRule("gdpr-export", "POST", "/api/users/gdpr/export", 5, Duration.ofHours(1), RateLimitKeyType.USER),
                // Expensive per-student write: the photo upload (image decoding + disk).
                new RateLimitRule("photo-upload", "POST", "/api/quiz/attempts/*/responses/*/photo", 20, minute, RateLimitKeyType.USER),
                // Each one is a paid AI call over a whole test: bounds the provider bill like the import's daily pages.
                new RateLimitRule("en-chapter-suggestions", "POST", "/api/admin/quizzes/*/en-chapter-suggestions", 30,
                        Duration.ofDays(1), RateLimitKeyType.USER),
                // Three paid model calls per question (the tutor's levels): a whole paper twice over, per teacher per day.
                new RateLimitRule("tutor-hints", "POST", "/api/admin/quiz-items/*/tutor-hints", 60,
                        Duration.ofDays(1), RateLimitKeyType.USER)
        );
    }

    /**
     * Puts the filter in the servlet chain AFTER Spring Security (whose chain has a negative order), so the
     * SecurityContext is already populated when a USER-keyed rule asks who the caller is.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RedisRateLimitService service, List<RateLimitRule> rules, RateLimitPrincipalResolver principalResolver,
            @Value("${rate.limit.trust-x-forwarded-for:false}") boolean trustForwardedFor,
            MeterRegistry meterRegistry) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(service, rules, principalResolver, trustForwardedFor, meterRegistry));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
