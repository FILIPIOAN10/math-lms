package ro.mathlms.quiz;

/** A student tried to act on a quiz attempt that is not theirs. */
public class QuizAccessException extends RuntimeException {
    public QuizAccessException(String message) {
        super(message);
    }
}
