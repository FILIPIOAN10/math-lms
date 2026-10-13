package ro.mathlms.enchapter;

/** No such chapter (or no such photo / example inside it): a 404. */
public class EnChapterNotFoundException extends RuntimeException {
    public EnChapterNotFoundException(String what, Long id) {
        super("No " + what + " with id " + id);
    }
}
