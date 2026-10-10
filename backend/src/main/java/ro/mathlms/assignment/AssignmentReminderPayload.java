package ro.mathlms.assignment;

/**
 * Outbox payload of one "homework due soon" email. Ids only - the handler re-reads the current state, and no personal
 * data is stored in the outbox. One event per recipient (the student, and their linked parent), so a retry never
 * re-mails someone who already got it. {@code recipient} is null in rows written before parents were reminded: those
 * go to the student.
 */
public record AssignmentReminderPayload(Long assignmentId, Long studentId, Recipient recipient) {

    public enum Recipient {
        STUDENT,
        PARENT
    }

    /** The student's own reminder. */
    public AssignmentReminderPayload(Long assignmentId, Long studentId) {
        this(assignmentId, studentId, Recipient.STUDENT);
    }

    public boolean forParent() {
        return recipient == Recipient.PARENT;
    }
}
