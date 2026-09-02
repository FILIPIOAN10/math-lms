package ro.mathlms.content;

/** A uniqueness rule would be violated (e.g. two books with the same title in one class). */
public class DuplicateContentException extends RuntimeException {
    public DuplicateContentException(String message) {
        super(message);
    }
}
