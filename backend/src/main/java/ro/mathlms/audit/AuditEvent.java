package ro.mathlms.audit;

import ro.mathlms.user.User;

/**
 * One thing to write to the journal, built where the change happens:
 * {@code AuditEvent.of(ANSWER_GRADED, attemptId, "Teză · întrebarea 3").about(student).details("2 → 4 p din 5")}.
 * {@code label} and {@code details} must not contain a person's name or email (see V26) - people go in
 * {@code studentId} (whom it is about) and {@code relatedUserId} (a second person, e.g. the parent linked).
 */
public record AuditEvent(AuditAction action, Long entityId, String label, String details, Long studentId,
                         Long relatedUserId) {

    public static AuditEvent of(AuditAction action, Long entityId, String label) {
        return new AuditEvent(action, entityId, label, null, null, null);
    }

    public AuditEvent details(String details) {
        return new AuditEvent(action, entityId, label, details, studentId, relatedUserId);
    }

    /** The person the change is about (nearly always a student); {@code null} leaves it unset. */
    public AuditEvent about(User student) {
        return new AuditEvent(action, entityId, label, details, idOf(student), relatedUserId);
    }

    /** A second person involved, e.g. the parent linked to the student. */
    public AuditEvent with(User related) {
        return new AuditEvent(action, entityId, label, details, studentId, idOf(related));
    }

    private static Long idOf(User user) {
        return user == null ? null : user.getId();
    }
}
