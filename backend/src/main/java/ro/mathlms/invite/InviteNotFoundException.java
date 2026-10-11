package ro.mathlms.invite;

/** The class, student or invite link does not exist. */
public class InviteNotFoundException extends RuntimeException {
    public InviteNotFoundException(String message) {
        super(message);
    }
}
