package ro.mathlms.enchapter;

/** A change that clashes with what exists (a code already taken, a page that still has content): a 409. */
public class EnChapterConflictException extends RuntimeException {
    public EnChapterConflictException(String message) {
        super(message);
    }
}
