package ro.mathlms.grading;

/** A teacher already has the most saved comments allowed: 409 with the reason, in Romanian. */
public class SnippetLimitException extends RuntimeException {

    public SnippetLimitException(String message) {
        super(message);
    }
}
