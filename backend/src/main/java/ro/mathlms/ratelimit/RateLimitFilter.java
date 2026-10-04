package ro.mathlms.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Enforces the {@link RateLimitRule}s: for each request find the first rule matching method + path, check its
 * counter in Redis, expose X-RateLimit-* headers, and over the limit answer 429 + Retry-After (a plain-text
 * Romanian message — the SPA shows an error body as-is) instead of calling the rest of the chain.
 *
 * <p>Registered by {@link RateLimitConfig} (not a {@code @Component}: web-slice tests scan every Filter bean and
 * would then demand Redis). It runs after the Spring Security chain, so the user is already authenticated.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimitService service;
    private final List<RateLimitRule> rules;
    private final RateLimitPrincipalResolver principalResolver;
    private final boolean trustForwardedFor;
    private final MeterRegistry meterRegistry;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(RedisRateLimitService service, List<RateLimitRule> rules,
                           RateLimitPrincipalResolver principalResolver, boolean trustForwardedFor,
                           MeterRegistry meterRegistry) {
        this.service = service;
        this.rules = rules;
        this.principalResolver = principalResolver;
        this.trustForwardedFor = trustForwardedFor;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<RateLimitRule> matched = findRule(request);
        if (matched.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitRule rule = matched.get();
        RateLimitResult result = service.checkLimit(redisKey(request, rule), rule);

        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingRequests()));
        if (!result.allowed()) {
            // bounded tag (one value per rule): how often each limit bites — a spike means an attack or a too-tight rule
            meterRegistry.counter("rate_limit_blocked", "rule", rule.name()).increment();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType("text/plain;charset=UTF-8");
            response.getOutputStream().write(("Prea multe cereri. Încearcă din nou peste "
                    + result.retryAfterSeconds() + " secunde.").getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<RateLimitRule> findRule(HttpServletRequest request) {
        return rules.stream()
                .filter(rule -> rule.method().equalsIgnoreCase(request.getMethod()))
                .filter(rule -> pathMatcher.match(rule.pathPattern(), request.getRequestURI()))
                .findFirst();
    }

    private String redisKey(HttpServletRequest request, RateLimitRule rule) {
        String identity = "ip:" + clientIp(request);
        if (rule.keyType() == RateLimitKeyType.USER) {
            String user = principalResolver.resolveUser(request);
            if (user != null && !user.isBlank()) {
                identity = "user:" + user;
            }
        }
        return "rate_limit:" + rule.name() + ":" + identity;
    }

    /** X-Forwarded-For is honoured only when told to: otherwise any client could pick its own bucket. */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (trustForwardedFor && forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
