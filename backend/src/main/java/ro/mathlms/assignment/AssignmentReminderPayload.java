package ro.mathlms.assignment;

/**
 * Outbox payload of one "homework due soon" email. Ids only - the handler re-reads the current state, and no personal
 * data is stored in the outbox. One event per student, so a retry never re-mails someone who already got it.
 */
public record AssignmentReminderPayload(Long assignmentId, Long studentId) {
}
