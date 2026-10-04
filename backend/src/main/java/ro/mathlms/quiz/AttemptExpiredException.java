package ro.mathlms.quiz;

/** The attempt's time ran out: no more answers are accepted (HTTP 409). Submitting is still allowed. */
public class AttemptExpiredException extends InvalidQuizException {
    public AttemptExpiredException() {
        super("Timpul pentru acest test a expirat");
    }
}
