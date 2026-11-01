package ro.mathlms.assignment;

import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * How far one student got on one assignment, derived (never stored) from their attempts at the quiz. Only graded
 * TESTs started since the assignment was created count - a practice never does, nor an attempt from before. The
 * earliest hand-in says whether it was on time, so a later retake cannot turn an on-time hand-in into a late one; the
 * highest grade is the one shown, as everywhere a student has retaken a test.
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

        List<QuizAttempt> handedIn = counted.stream()
                .filter(a -> a.getStatus() != QuizAttemptStatus.IN_PROGRESS)
                .toList();
        // Highest score first, then the earlier one: that attempt is the grade this homework shows.
        Optional<QuizAttempt> best = handedIn.stream()
                .filter(a -> a.getStatus() == QuizAttemptStatus.GRADED)
                .max(Comparator.comparing(QuizAttempt::getScore)
                        .thenComparing(QuizAttempt::getSubmittedAt, Comparator.reverseOrder()));

        return handedIn.stream()
                .min(Comparator.comparing(QuizAttempt::getSubmittedAt))
                .map(first -> new AssignmentProgress(
                        best.isPresent() ? State.GRADED : State.SUBMITTED,
                        first.getSubmittedAt(),
                        first.getSubmittedAt().isAfter(assignment.getDueAt()),
                        best.map(QuizAttempt::getScore).orElse(null),
                        best.orElse(first).getId()))
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
