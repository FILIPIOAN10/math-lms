package ro.mathlms.auth;

/** The presented refresh token is missing, expired, revoked, or not the caller's. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
