package ro.mathlms.ratelimit;

/** Outcome of one limit check: allowed or not, how many requests are left, and seconds until the window resets. */
public record RateLimitResult(boolean allowed, long currentRequests, long remainingRequests, long retryAfterSeconds) {
}
