package ro.mathlms.quiz;

/** A quiz or quiz item does not exist for the given id. */
public class QuizNotFoundException extends RuntimeException {
    public QuizNotFoundException(String what, Long id) {
        super("No " + what + " with id " + id);
    }
}
