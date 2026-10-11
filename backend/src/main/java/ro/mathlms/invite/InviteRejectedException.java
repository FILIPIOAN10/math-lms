package ro.mathlms.invite;

/** The teacher asked for a link that does not fit, e.g. a parent link for a student who already has a parent. */
public class InviteRejectedException extends RuntimeException {
    public InviteRejectedException(String message) {
        super(message);
    }
}
