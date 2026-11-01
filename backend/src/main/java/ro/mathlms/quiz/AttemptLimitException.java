package ro.mathlms.quiz;

/**
 * A student may not start another graded attempt (see {@link AttemptAllowance}). Its own type so that starting can keep
 * what it already did: an overdue attempt handed in on the way here stays handed in.
 */
public class AttemptLimitException extends InvalidQuizException {

    public AttemptLimitException(RetakeBlock reason) {
        super(reason.message());
    }
}
