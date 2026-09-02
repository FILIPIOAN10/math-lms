package ro.mathlms.content;

/** A content entity (class, book, chapter, exercise) does not exist for the given id. */
public class ContentNotFoundException extends RuntimeException {
    public ContentNotFoundException(String what, Long id) {
        super("No " + what + " with id " + id);
    }
}
