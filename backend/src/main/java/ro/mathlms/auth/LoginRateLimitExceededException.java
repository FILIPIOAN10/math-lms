package ro.mathlms.auth;

/** The account-wide login bucket is full; the email itself is never included in the response or logs. */
public class LoginRateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginRateLimitExceededException(long retryAfterSeconds) {
        super("Too many login attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
