package ro.mathlms.quiz;

import java.time.Instant;
import java.util.List;

/**
 * Whether a student may start a graded attempt at a quiz, derived (never stored) from their attempts at it.
 * <ul>
 *   <li>Only a handed-in test uses an attempt: a practice never does, and one still open is simply resumed.</li>
 *   <li>While a paper waits for its grade there is no retake: the student would be guessing at a result still to come.</li>
 *   <li>Homework starts the count again: what was handed in before the quiz was given as homework does not count (the
 *       same rule as {@code AssignmentProgress}), so nobody is given homework they can no longer start.</li>
 * </ul>
 *
 * @param maxAttempts null = no limit
 * @param used        the handed-in tests that count
 * @param blockedBy   null = the student may start (or resume)
 */
public record AttemptAllowance(Integer maxAttempts, int used, RetakeBlock blockedBy) {

    /** {@code attempts} are the student's attempts at the quiz (any mode, any status); {@code homeworkGivenAt} may be null. */
    public static AttemptAllowance of(Integer maxAttempts, List<QuizAttempt> attempts, Instant homeworkGivenAt) {
        List<QuizAttempt> tests = attempts.stream().filter(a -> a.getMode() == AttemptMode.TEST).toList();
        List<QuizAttempt> handedIn = tests.stream()
                .filter(a -> a.getStatus() != QuizAttemptStatus.IN_PROGRESS)
                .filter(a -> homeworkGivenAt == null || !a.getStartedAt().isBefore(homeworkGivenAt))
                .toList();
        int used = handedIn.size();
        boolean open = tests.stream().anyMatch(a -> a.getStatus() == QuizAttemptStatus.IN_PROGRESS);
        if (open) {
            return new AttemptAllowance(maxAttempts, used, null);
        }
        if (handedIn.stream().anyMatch(a -> a.getStatus() == QuizAttemptStatus.SUBMITTED)) {
            return new AttemptAllowance(maxAttempts, used, RetakeBlock.WAITING_FOR_GRADE);
        }
        if (maxAttempts != null && used >= maxAttempts) {
            return new AttemptAllowance(maxAttempts, used, RetakeBlock.NO_ATTEMPTS_LEFT);
        }
        return new AttemptAllowance(maxAttempts, used, null);
    }
}
