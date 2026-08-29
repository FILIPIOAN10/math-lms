package ro.mathlms.auth;

public class EmailNotAllowedException extends RuntimeException {

    public EmailNotAllowedException(String email) {
        super("Email not allowed to sign in: " + email);
    }
}
