package ro.mathlms.tutor;

/** The tutor could not answer; the message is worded for the teacher. */
public class TutorException extends RuntimeException {

    public TutorException(String message) {
        super(message);
    }

    public TutorException(String message, Throwable cause) {
        super(message, cause);
    }
}
