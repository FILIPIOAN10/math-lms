package ro.mathlms.ratelimit;

import java.time.Duration;

/** One rule: at most {@code limit} requests per {@code window}, matched by HTTP method + Ant path, keyed by IP or user. */
public record RateLimitRule(String name, String method, String pathPattern, long limit, Duration window,
                            RateLimitKeyType keyType, boolean failClosed) {

    /** Existing/business routes retain fail-open behavior unless a rule explicitly requires the store. */
    public RateLimitRule(String name, String method, String pathPattern, long limit, Duration window,
                         RateLimitKeyType keyType) {
        this(name, method, pathPattern, limit, window, keyType, false);
    }
}
