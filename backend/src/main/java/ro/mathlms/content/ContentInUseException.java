package ro.mathlms.content;

/** The item still has content hanging off it, so deleting it would orphan or lose that content (→ 409). */
public class ContentInUseException extends RuntimeException {
    public ContentInUseException(String message) {
        super(message);
    }
}
