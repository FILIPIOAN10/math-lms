package ro.mathlms.auth;

/** Login rate limiting is fail-closed when Redis cannot enforce the account bucket. */
public class LoginRateLimitUnavailableException extends RuntimeException {
    public LoginRateLimitUnavailableException(Throwable cause) {
        super("Login rate-limit store unavailable", cause);
    }
}
