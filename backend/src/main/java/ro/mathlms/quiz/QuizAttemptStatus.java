package ro.mathlms.quiz;

/**
 * Lifecycle of a student's {@link QuizAttempt}:
 * <ul>
 *   <li>{@code IN_PROGRESS} — the student is still answering; responses can be recorded.</li>
 *   <li>{@code SUBMITTED} — handed in; SINGLE_CHOICE items are auto-graded, OPEN items await the teacher.</li>
 *   <li>{@code GRADED} — every item has a score; the total is final.</li>
 * </ul>
 */
public enum QuizAttemptStatus {
    IN_PROGRESS,
    SUBMITTED,
    GRADED
}
