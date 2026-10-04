package ro.mathlms.parent;

/**
 * A parent asked for a student who is not their child, or an attempt that is not that child's.
 * One exception for "not yours" and "does not exist" so the answer never confirms that an id exists.
 */
public class ParentAccessException extends RuntimeException {
    public ParentAccessException(String message) {
        super(message);
    }
}
