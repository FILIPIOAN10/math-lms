package ro.mathlms.assignment;

import jakarta.validation.constraints.NotNull;
import ro.mathlms.assignment.AssignmentProgress.State;

import java.time.Instant;

/** Request and response shapes of the homework API. */
public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    public record AssignmentRequest(@NotNull Long quizId, @NotNull Long schoolClassId, @NotNull Instant dueAt) {
    }

    public record RescheduleRequest(@NotNull Instant dueAt) {
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
            int notStarted
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
            Long attemptId
    ) {
    }

    /** One assignment as a student sees it. */
    public record StudentAssignmentDto(
            Long assignmentId,
            Long quizId,
            String quizTitle,
            String schoolClassName,
            Instant dueAt,
            State state,
            boolean late,     // handed in after the deadline
            boolean overdue,  // deadline passed and not handed in yet
            Long attemptId,   // the open attempt to continue, or the handed-in one whose result to read
            Integer timeLimitMinutes
    ) {
    }
}
