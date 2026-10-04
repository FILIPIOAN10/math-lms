package ro.mathlms.ratelimit;

import java.time.Duration;

/** One rule: at most {@code limit} requests per {@code window}, matched by HTTP method + Ant path, keyed by IP or user. */
public record RateLimitRule(String name, String method, String pathPattern, long limit, Duration window,
                            RateLimitKeyType keyType) {
}
