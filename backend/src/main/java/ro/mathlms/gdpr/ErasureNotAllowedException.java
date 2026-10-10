package ro.mathlms.gdpr;

/** An account that may not erase itself from inside the app (the teacher's); the message is shown as is. */
public class ErasureNotAllowedException extends RuntimeException {

    public ErasureNotAllowedException(String message) {
        super(message);
    }
}
