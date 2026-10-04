package ro.mathlms.ratelimit;

/** How a rule buckets requests: by client IP, or by the authenticated user (falls back to IP when anonymous). */
public enum RateLimitKeyType {
    IP,
    USER
}
