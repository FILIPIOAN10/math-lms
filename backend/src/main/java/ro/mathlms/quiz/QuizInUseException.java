package ro.mathlms.quiz;

/** Students already have attempts on the quiz; deleting it would destroy their answers and grades (→ 409). */
public class QuizInUseException extends RuntimeException {
    public QuizInUseException(String message) {
        super(message);
    }
}
