package ro.mathlms.quiz;

import java.time.Instant;
import java.util.List;

/**
 * Teacher-side views of student attempts (the grading screen). Unlike {@link StudentQuizDtos}
 * these carry the correct answer and the barem — this is the ADMIN view.
 */
public final class AdminAttemptDtos {

    private AdminAttemptDtos() {
    }

    /** One row of the grading queue. */
    public record AdminAttemptSummaryDto(
            Long attemptId,
            Long quizId,
            String quizTitle,
            Long studentId,
            String studentName,
            QuizAttemptStatus status,
            Instant submittedAt,
            Integer score,
            int maxScore
    ) {
        public static AdminAttemptSummaryDto from(QuizAttempt attempt, int maxScore) {
            return new AdminAttemptSummaryDto(
                    attempt.getId(),
                    attempt.getQuiz().getId(),
                    attempt.getQuiz().getTitle(),
                    attempt.getStudent().getId(),
                    attempt.getStudent().getFullName(),
                    attempt.getStatus(),
                    attempt.getSubmittedAt(),
                    attempt.getScore(),
                    maxScore);
        }
    }

    /** One item of an attempt as the teacher reviews it. {@code itemId} is what the grade call needs. */
    public record AdminItemReviewDto(
            Long itemId,
            int position,
            QuizItemType type,
            String statement,
            int points,
            String barem,
            String selectedOptionText,
            String correctOptionText,
            Boolean correct,
            Integer awardedPoints,
            boolean photoUploaded,
            String teacherComment
    ) {
    }

    /** A whole attempt for grading: header + every item, in quiz order. */
    public record AdminAttemptDetailDto(
            Long attemptId,
            String quizTitle,
            String studentName,
            QuizAttemptStatus status,
            Instant submittedAt,
            Integer score,
            int maxScore,
            List<AdminItemReviewDto> items,
            String teacherComment // on the whole paper
    ) {
    }
}
