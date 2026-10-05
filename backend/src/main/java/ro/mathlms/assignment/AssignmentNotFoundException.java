package ro.mathlms.assignment;

/** No assignment with that id. */
public class AssignmentNotFoundException extends RuntimeException {
    public AssignmentNotFoundException(String message) {
        super(message);
    }
}
