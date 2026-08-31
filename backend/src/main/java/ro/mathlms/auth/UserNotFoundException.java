package ro.mathlms.auth;

/** No account exists for the given id (admin acted on a stale/unknown user). */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("No account with id " + id);
    }
}
