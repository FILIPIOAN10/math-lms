package ro.mathlms.invite;

/** The invite link can no longer be used (unknown, revoked, expired or full); the message says which, in Romanian. */
public class InviteUnavailableException extends RuntimeException {
    public InviteUnavailableException(String message) {
        super(message);
    }
}
