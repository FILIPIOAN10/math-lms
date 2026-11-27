package ro.mathlms.recap;

/** What the teacher asks cannot be done in the collection's present state (409), in Romanian for the screen. */
public class RecapConflictException extends RuntimeException {
    public RecapConflictException(String message) {
        super(message);
    }
}
