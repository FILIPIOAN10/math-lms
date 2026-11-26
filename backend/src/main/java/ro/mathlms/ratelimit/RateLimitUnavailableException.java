package ro.mathlms.ratelimit;

/** The counter store is unavailable; the HTTP filter decides whether this route fails open or closed. */
public class RateLimitUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RateLimitUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
