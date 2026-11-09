package ro.mathlms.assignment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ro.mathlms.assignment.AssignmentProgress.State;

import java.time.Instant;
import java.util.List;

/** Request and response shapes of the homework API. */
public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    public record AssignmentRequest(@NotNull Long quizId, @NotNull Long schoolClassId, @NotNull Instant dueAt) {
    }

    public record RescheduleRequest(@NotNull Instant dueAt) {
    }

    /** One quiz given to several classes at once, each with the same deadline. */
    public record MultiClassAssignmentRequest(@NotNull Long quizId, @NotEmpty List<@NotNull Long> schoolClassIds,
                                              @NotNull Instant dueAt) {
    }

    /**
     * One student's exception to a homework: their own deadline (null = the class one) and/or excused. Neither clears
     * the exception.
     */
    public record OverrideRequest(Instant dueAt, boolean excused) {
    }

    /** T3: the class and deadline of the homework given together with publishing (the quiz is in the path). */
    public record PublishAndAssignRequest(@NotNull Long schoolClassId, @NotNull Instant dueAt) {
    }

    /** One assignment as the teacher's list shows it, with how the class is doing. */
    public record AssignmentSummaryDto(
            Long id,
            Long quizId,
            String quizTitle,
            Long schoolClassId,
            String schoolClassName,
            Instant dueAt,
            boolean overdue,
            int enrolled,
            int done,        // handed in, on time or late
            int late,        // of those, handed in after the deadline
            int inProgress,
            int notStarted,
            int excused      // left out of every count above: the homework does not count for them
    ) {
    }

    /** One student's row in the teacher's per-assignment status. */
    public record StudentStatusDto(
            Long studentId,
            String fullName,
            State state,
            Instant submittedAt,
            boolean late,
            Integer score,
            Long attemptId,
            Instant ownDueAt, // the student's own deadline; null = the class one
            boolean excused
    ) {
    }

    /** One assignment as a student sees it. */
    public record StudentAssignmentDto(
            Long assignmentId,
            Long quizId,
            String quizTitle,
            String schoolClassName,
            Instant dueAt,    // the student's own deadline when the teacher gave them one, else the class one
            State state,
            boolean late,     // handed in after the deadline
            boolean overdue,  // deadline passed and not handed in yet
            Long attemptId,   // the open attempt to continue, or the handed-in one whose result to read
            Integer timeLimitMinutes,
            boolean excused   // the homework does not count for this student (only the teacher's view lists it)
    ) {
    }
}
