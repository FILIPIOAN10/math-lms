package ro.mathlms.assignment;

/** This quiz is already assigned to that class. */
public class AssignmentConflictException extends RuntimeException {
    public AssignmentConflictException(String message) {
        super(message);
    }
}
