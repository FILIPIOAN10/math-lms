package ro.mathlms.assignment;

/** The assignment breaks a rule (draft quiz, wrong class, deadline in the past...). */
public class InvalidAssignmentException extends RuntimeException {
    public InvalidAssignmentException(String message) {
        super(message);
    }
}
