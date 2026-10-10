package ro.mathlms.auth;

/** A password change the account cannot make; the message is shown to the user as is (Romanian). */
public class PasswordChangeException extends RuntimeException {

    public PasswordChangeException(String message) {
        super(message);
    }
}
