package ro.mathlms.quiz;

/** A quiz-authoring rule was violated (e.g. a grilă without exactly one correct option). */
public class InvalidQuizException extends RuntimeException {
    public InvalidQuizException(String message) {
        super(message);
    }
}
