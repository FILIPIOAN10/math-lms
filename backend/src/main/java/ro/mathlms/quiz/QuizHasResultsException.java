package ro.mathlms.quiz;

/**
 * A plain delete of a test students already handed in: refused (409), with how many attempts a delete
 * {@code withResults} would take along, so the menu can ask a second, explicit time.
 */
public class QuizHasResultsException extends QuizInUseException {

    private final long handedIn;

    public QuizHasResultsException(String message, long handedIn) {
        super(message);
        this.handedIn = handedIn;
    }

    public long handedIn() {
        return handedIn;
    }
}
