package ro.mathlms.assignment;

import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * How far one student got on one assignment, derived (never stored) from their attempts at the quiz. Only graded
 * TESTs started since the assignment was created count - a practice never does, nor an attempt from before. The
 * earliest handed-in attempt is the one that counts, so a later retake cannot turn an on-time hand-in into a late one.
 */
public record AssignmentProgress(State state, Instant submittedAt, boolean late, Integer score, Long attemptId) {

    public enum State {
        NOT_STARTED,
        IN_PROGRESS,
        /** Handed in; a teacher may still have open items to mark. */
        SUBMITTED,
        GRADED
    }

    /** {@code attempts} are the student's attempts at the assignment's quiz (any mode, any status, any order). */
    public static AssignmentProgress of(Assignment assignment, List<QuizAttempt> attempts) {
        List<QuizAttempt> counted = attempts.stream()
                .filter(a -> a.getMode() == AttemptMode.TEST)
                .filter(a -> !a.getStartedAt().isBefore(assignment.getCreatedAt()))
                .toList();

        return counted.stream()
                .filter(a -> a.getStatus() != QuizAttemptStatus.IN_PROGRESS)
                .min(Comparator.comparing(QuizAttempt::getSubmittedAt))
                .map(done -> new AssignmentProgress(
                        done.getStatus() == QuizAttemptStatus.GRADED ? State.GRADED : State.SUBMITTED,
                        done.getSubmittedAt(),
                        done.getSubmittedAt().isAfter(assignment.getDueAt()),
                        done.getScore(),
                        done.getId()))
                .orElseGet(() -> counted.stream()
                        .filter(a -> a.getStatus() == QuizAttemptStatus.IN_PROGRESS)
                        .findFirst()
                        .map(open -> new AssignmentProgress(State.IN_PROGRESS, null, false, null, open.getId()))
                        .orElse(new AssignmentProgress(State.NOT_STARTED, null, false, null, null)));
    }

    /** Handed in (whether or not it is marked yet). */
    public boolean isDone() {
        return state == State.SUBMITTED || state == State.GRADED;
    }
}
