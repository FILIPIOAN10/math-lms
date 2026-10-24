package ro.mathlms.auth;

/** A two-step sign-in request that cannot go ahead (wrong code, not staff, required for admins...): 400 + the reason. */
public class TwoFactorException extends RuntimeException {
    public TwoFactorException(String message) {
        super(message);
    }
}
