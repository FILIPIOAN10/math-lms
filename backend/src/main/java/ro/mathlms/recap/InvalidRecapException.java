package ro.mathlms.recap;

/** A request or an imported file the collection cannot take (400), in Romanian for the screen. */
public class InvalidRecapException extends RuntimeException {
    public InvalidRecapException(String message) {
        super(message);
    }
}
